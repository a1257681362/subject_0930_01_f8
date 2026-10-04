package com.evops.coldchain.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.coldchain.entity.CarrierQuote;
import com.evops.coldchain.entity.OpLog;
import com.evops.coldchain.entity.PriceAdjustment;
import com.evops.coldchain.entity.PriceAdjustmentSurcharge;
import com.evops.coldchain.entity.PriceLock;
import com.evops.coldchain.entity.QuoteBatch;
import com.evops.coldchain.entity.QuoteException;
import com.evops.coldchain.entity.QuoteSurcharge;
import com.evops.coldchain.entity.TransportBatch;
import com.evops.coldchain.mapper.OpLogMapper;
import com.evops.coldchain.mapper.TransportBatchMapper;
import com.evops.common.BusinessException;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 锁价反查服务（V8）：从冷链药品运输的锁价单（或已派车运输批次）反向定位
 * 原报价单、各环节处理人以及报价异常原因，形成“运输批次 -> 锁价单 -> 中选原报价/比选批次 ->
 * 异常与处理 -> 改价审批链”的完整追溯链。全部为只读查询，不改变任何报价事实。
 */
@Service
public class PriceLockTraceService {

    @Resource
    private QuoteBatchService quoteBatchService;
    @Resource
    private QuoteExceptionService quoteExceptionService;
    @Resource
    private PriceAdjustmentService adjustmentService;
    @Resource
    private TransportBatchMapper batchMapper;
    @Resource
    private OpLogMapper opLogMapper;

    /** 按锁价单ID反查完整链路 */
    public Map<String, Object> traceByLock(Long priceLockId) {
        PriceLock lock = quoteBatchService.requirePriceLock(priceLockId);
        return buildTrace(lock);
    }

    /** 按运输批次ID反查：批次 -> 锁价单 -> 原报价/异常/改价链 */
    public Map<String, Object> traceByBatch(Long transportBatchId) {
        TransportBatch batch = batchMapper.selectById(transportBatchId);
        if (batch == null) {
            throw new BusinessException("运输批次不存在: " + transportBatchId);
        }
        if (batch.getPriceLockId() == null) {
            throw new BusinessException("运输批次 " + batch.getBatchNo() + " 未关联锁价单，无报价比选记录可反查");
        }
        PriceLock lock = quoteBatchService.requirePriceLock(batch.getPriceLockId());
        Map<String, Object> trace = buildTrace(lock);
        trace.put("transportBatchId", batch.getId());
        trace.put("transportBatchNo", batch.getBatchNo());
        return trace;
    }

    private Map<String, Object> buildTrace(PriceLock lock) {
        QuoteBatch quoteBatch = quoteBatchService.requireQuoteBatch(lock.getQuoteBatchId());
        CarrierQuote wonQuote = quoteBatchService.requireQuote(lock.getWonQuoteId());
        List<QuoteSurcharge> wonSurcharges = quoteBatchService.listSurcharges(wonQuote.getId());

        // 该比选批次的全部报价（中选、落选、撤报、过期），含金额与附加费
        Map<String, Object> comparisonView = quoteBatchService.comparison(quoteBatch.getId());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> allQuotes = (List<Map<String, Object>>) comparisonView.get("quotes");

        // 异常（批次级 + 报价级），含处理人与处理说明——“异常原因/处理人”反查的核心
        List<QuoteException> exceptions = quoteExceptionService.listByBatch(quoteBatch.getId());

        // 改价审批链（申请/同意/驳回，逐单保留）
        List<PriceAdjustment> adjustments = adjustmentService.listByLock(lock.getId());
        List<Map<String, Object>> adjustmentList = new ArrayList<>();
        for (PriceAdjustment a : adjustments) {
            List<PriceAdjustmentSurcharge> surcharges = adjustmentService.listAdjustmentSurcharges(a.getId());
            Map<String, Object> am = new LinkedHashMap<>();
            am.put("adjustment", a);
            am.put("surcharges", surcharges);
            adjustmentList.add(am);
        }

        // 关键操作留痕：锁价单、中选报价、改价审批、报价异常的操作者与时间
        List<OpLog> priceOpLogs = opLogMapper.selectList(new QueryWrapper<OpLog>()
                .in("entity_type", java.util.Arrays.asList(
                        com.evops.coldchain.CcConstants.ENT_PRICE_LOCK,
                        com.evops.coldchain.CcConstants.ENT_QUOTE,
                        com.evops.coldchain.CcConstants.ENT_PRICE_ADJUSTMENT,
                        com.evops.coldchain.CcConstants.ENT_QUOTE_EXCEPTION,
                        com.evops.coldchain.CcConstants.ENT_QUOTE_BATCH))
                .orderByAsc("id"));
        List<OpLog> relatedLogs = new ArrayList<>();
        for (OpLog log : priceOpLogs) {
            if (relatedTo(log, lock, wonQuote, quoteBatch, adjustments, exceptions)) {
                relatedLogs.add(log);
            }
        }

        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("priceLock", lock);
        trace.put("quoteBatch", quoteBatch);
        trace.put("wonQuote", wonQuote);
        trace.put("wonSurcharges", wonSurcharges);
        trace.put("allQuotes", allQuotes);
        trace.put("quoteExceptions", exceptions);
        trace.put("priceAdjustments", adjustmentList);
        trace.put("handlers", summarizeHandlers(lock, wonQuote, exceptions, adjustments));
        trace.put("opLogs", relatedLogs);
        return trace;
    }

    private boolean relatedTo(OpLog log, PriceLock lock, CarrierQuote wonQuote, QuoteBatch batch,
                              List<PriceAdjustment> adjustments, List<QuoteException> exceptions) {
        Long eid = log.getEntityId();
        if (eid == null) {
            return false;
        }
        switch (log.getEntityType()) {
            case com.evops.coldchain.CcConstants.ENT_PRICE_LOCK:
                return eid.equals(lock.getId());
            case com.evops.coldchain.CcConstants.ENT_PRICE_ADJUSTMENT:
                for (PriceAdjustment a : adjustments) {
                    if (eid.equals(a.getId())) {
                        return true;
                    }
                }
                return false;
            case com.evops.coldchain.CcConstants.ENT_QUOTE_EXCEPTION:
                for (QuoteException e : exceptions) {
                    if (eid.equals(e.getId())) {
                        return true;
                    }
                }
                return false;
            case com.evops.coldchain.CcConstants.ENT_QUOTE:
                return eid.equals(wonQuote.getId());
            case com.evops.coldchain.CcConstants.ENT_QUOTE_BATCH:
                return eid.equals(batch.getId());
            default:
                return false;
        }
    }

    /** 汇总各环节处理人，直接回应“反查处理人”的诉求 */
    private Map<String, Object> summarizeHandlers(PriceLock lock, CarrierQuote wonQuote,
                                                   List<QuoteException> exceptions,
                                                   List<PriceAdjustment> adjustments) {
        Map<String, Object> handlers = new LinkedHashMap<>();
        handlers.put("quoteSubmitName", wonQuote.getSubmitName());
        handlers.put("lockedByName", lock.getLockedName());
        List<Map<String, Object>> excHandlers = new ArrayList<>();
        for (QuoteException e : exceptions) {
            Map<String, Object> em = new LinkedHashMap<>();
            em.put("exceptionId", e.getId());
            em.put("exceptionType", e.getExceptionType());
            em.put("carrierName", e.getCarrierName());
            em.put("status", e.getStatus());
            em.put("resolveName", e.getResolveName());
            em.put("description", e.getDescription());
            em.put("resolveNote", e.getResolveNote());
            excHandlers.add(em);
        }
        handlers.put("quoteExceptions", excHandlers);
        List<Map<String, Object>> adjHandlers = new ArrayList<>();
        for (PriceAdjustment a : adjustments) {
            Map<String, Object> am = new LinkedHashMap<>();
            am.put("adjustmentNo", a.getAdjustmentNo());
            am.put("status", a.getStatus());
            am.put("applyName", a.getApplyName());
            am.put("reviewName", a.getReviewName());
            adjHandlers.add(am);
        }
        handlers.put("priceAdjustments", adjHandlers);
        return handlers;
    }
}

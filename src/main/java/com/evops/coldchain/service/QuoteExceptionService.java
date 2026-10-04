package com.evops.coldchain.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.coldchain.CcConstants;
import com.evops.coldchain.dto.QuoteExceptionMarkRequest;
import com.evops.coldchain.dto.QuoteExceptionResolveRequest;
import com.evops.coldchain.entity.CarrierQuote;
import com.evops.coldchain.entity.QuoteBatch;
import com.evops.coldchain.entity.QuoteException;
import com.evops.coldchain.mapper.QuoteExceptionMapper;
import com.evops.common.BusinessException;
import com.evops.common.UserContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 报价异常服务（V8）：报价超时未报/异常撤报/计价争议/资质不符等异常只追加登记，
 * 闭环只追加处理说明与处理人（不改原描述、不删行）。异常是锁价单反查“异常原因与处理人”的数据源。
 */
@Service
public class QuoteExceptionService {

    @Resource
    private QuoteExceptionMapper exceptionMapper;
    @Resource
    private QuoteBatchService quoteBatchService;
    @Resource
    private OpLogService opLogService;

    @Transactional
    public QuoteException mark(QuoteExceptionMarkRequest req) {
        QuoteBatch batch = quoteBatchService.requireQuoteBatch(req.getQuoteBatchId());
        String type = normalizeType(req.getExceptionType());

        Long carrierId = null;
        String carrierName = null;
        if (req.getQuoteId() != null) {
            CarrierQuote quote = quoteBatchService.requireQuote(req.getQuoteId());
            if (!quote.getQuoteBatchId().equals(batch.getId())) {
                throw new BusinessException(String.format("报价 %s 不属于询价批次 %s",
                        quote.getQuoteNo(), batch.getQuoteBatchNo()));
            }
            carrierId = quote.getCarrierId();
            carrierName = quote.getCarrierNameSnap();
        }

        QuoteException exception = new QuoteException();
        exception.setQuoteBatchId(batch.getId());
        exception.setQuoteId(req.getQuoteId());
        exception.setCarrierId(carrierId);
        exception.setCarrierName(carrierName);
        exception.setExceptionType(type);
        exception.setDescription(req.getDescription().trim());
        exception.setStatus(CcConstants.EXC_OPEN);
        exceptionMapper.insert(exception);

        opLogService.log(CcConstants.ENT_QUOTE_EXCEPTION, exception.getId(), null,
                CcConstants.ACT_QUOTE_EXCEPTION_MARK,
                String.format("询价批次 %s 登记报价异常[%s]：承运商=%s，报价ID=%s，原因=%s",
                        batch.getQuoteBatchNo(), type,
                        carrierName == null ? "(批次级)" : carrierName,
                        req.getQuoteId() == null ? "-" : req.getQuoteId(),
                        exception.getDescription()));
        return exception;
    }

    /** 闭环：只追加处理说明、处理人与时间，不改原异常描述 */
    @Transactional
    public QuoteException resolve(QuoteExceptionResolveRequest req) {
        QuoteException exception = exceptionMapper.selectById(req.getId());
        if (exception == null) {
            throw new BusinessException("报价异常不存在: " + req.getId());
        }
        if (CcConstants.EXC_RESOLVED.equals(exception.getStatus())) {
            throw new BusinessException("报价异常已处理，不能重复处理");
        }
        exception.setStatus(CcConstants.EXC_RESOLVED);
        exception.setResolveNote(req.getResolveNote().trim());
        exception.setResolveName(UserContext.currentName());
        exception.setResolveTime(LocalDateTime.now());
        exceptionMapper.updateById(exception);

        opLogService.log(CcConstants.ENT_QUOTE_EXCEPTION, exception.getId(), null,
                CcConstants.ACT_QUOTE_EXCEPTION_RESOLVE,
                String.format("报价异常#%d（询价批次ID=%d，类型=%s）处理闭环：处理人=%s，说明=%s（原异常原因保留：%s）",
                        exception.getId(), exception.getQuoteBatchId(), exception.getExceptionType(),
                        exception.getResolveName(), exception.getResolveNote(), exception.getDescription()));
        return exception;
    }

    public List<QuoteException> listByBatch(Long quoteBatchId) {
        return exceptionMapper.selectList(new QueryWrapper<QuoteException>()
                .eq("quote_batch_id", quoteBatchId).orderByAsc("id"));
    }

    private String normalizeType(String type) {
        if (type == null) {
            return CcConstants.QEXC_OTHER;
        }
        switch (type.trim().toUpperCase()) {
            case CcConstants.QEXC_NO_RESPONSE:
            case CcConstants.QEXC_ABNORMAL_WITHDRAW:
            case CcConstants.QEXC_PRICE_DISPUTE:
            case CcConstants.QEXC_QUALIFICATION:
                return type.trim().toUpperCase();
            default:
                return CcConstants.QEXC_OTHER;
        }
    }
}

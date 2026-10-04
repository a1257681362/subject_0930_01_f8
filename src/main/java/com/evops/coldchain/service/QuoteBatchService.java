package com.evops.coldchain.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.coldchain.CcConstants;
import com.evops.coldchain.dto.PriceLockRequest;
import com.evops.coldchain.dto.QuoteBatchCreateRequest;
import com.evops.coldchain.dto.QuoteSubmitRequest;
import com.evops.coldchain.dto.QuoteWithdrawRequest;
import com.evops.coldchain.dto.SurchargeItem;
import com.evops.coldchain.entity.BoxType;
import com.evops.coldchain.entity.Carrier;
import com.evops.coldchain.entity.CarrierQuote;
import com.evops.coldchain.entity.PriceLock;
import com.evops.coldchain.entity.QuoteBatch;
import com.evops.coldchain.entity.QuoteSurcharge;
import com.evops.coldchain.entity.Route;
import com.evops.coldchain.mapper.BoxTypeMapper;
import com.evops.coldchain.mapper.CarrierMapper;
import com.evops.coldchain.mapper.CarrierQuoteMapper;
import com.evops.coldchain.mapper.PriceLockMapper;
import com.evops.coldchain.mapper.QuoteBatchMapper;
import com.evops.coldchain.mapper.QuoteSurchargeMapper;
import com.evops.coldchain.mapper.RouteMapper;
import com.evops.coldchain.mapper.SequenceMapper;
import com.evops.common.BusinessException;
import com.evops.common.UserContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 承运商报价比选服务（V8）。
 *
 * 不变量：
 * 1. 询价批次固化“路线 × 温区 × 箱型 × 服务等级”四个维度，多承运商并行报价（各自独立行，互不阻塞）；
 *    同一询价批次同一承运商至多一条 SUBMITTED 报价，撤报置 WITHDRAWN 后可报新行。
 * 2. 金额拆分：税额/含税总价由 {@link PriceCalculator} 按“基础运费 + Σ附加费”重算，附加费逐行留痕。
 * 3. 过期（valid_until &lt;= 当时）报价不能锁价、不能被新运输批次引用；扫描幂等推进 EXPIRED，不改金额、不删行。
 * 4. 锁价只能选唯一一个有效方案：中选报价 WON、其余有效报价 LOST、询价批次 CLOSED；
 *    中选报价全字段快照到锁价单，锁价后报价过期/再报价均与锁价单脱钩。
 * 5. 派车消费锁价单后置 CONSUMED 并回填运输批次；改价审批中（PRICE_PENDING）的锁价单不能被消费。
 */
@Service
public class QuoteBatchService {

    @Resource
    private QuoteBatchMapper quoteBatchMapper;
    @Resource
    private CarrierQuoteMapper quoteMapper;
    @Resource
    private QuoteSurchargeMapper surchargeMapper;
    @Resource
    private PriceLockMapper priceLockMapper;
    @Resource
    private CarrierMapper carrierMapper;
    @Resource
    private BoxTypeMapper boxTypeMapper;
    @Resource
    private RouteMapper routeMapper;
    @Resource
    private SequenceMapper sequenceMapper;
    @Resource
    private BoxTypeService boxTypeService;
    @Resource
    private OpLogService opLogService;

    private static final Map<String, String> ZONE_NAMES = new LinkedHashMap<>();
    private static final Map<String, String> SERVICE_LEVEL_NAMES = new LinkedHashMap<>();
    static {
        ZONE_NAMES.put(CcConstants.ZONE_REFRIGERATE, "冷藏温区(2~8℃)");
        ZONE_NAMES.put(CcConstants.ZONE_FROZEN, "冷冻温区(-25~-18℃)");
        ZONE_NAMES.put(CcConstants.ZONE_DEEP_FROZEN, "深冻温区(≤-18℃)");
        ZONE_NAMES.put(CcConstants.ZONE_AMBIENT, "常温温区");
        SERVICE_LEVEL_NAMES.put(CcConstants.SVC_STANDARD, "标准");
        SERVICE_LEVEL_NAMES.put(CcConstants.SVC_EXPRESS, "加急");
        SERVICE_LEVEL_NAMES.put(CcConstants.SVC_DEDICATED, "专车");
    }

    // ---------------- 询价批次 ----------------

    @Transactional
    public QuoteBatch createBatch(QuoteBatchCreateRequest req) {
        String zoneCode = normalizeZone(req.getZoneCode());
        String serviceLevel = normalizeServiceLevel(req.getServiceLevel());
        BoxType boxType = boxTypeService.requireActiveBoxType(req.getBoxTypeId());

        String routeName = null;
        if (req.getRouteId() != null) {
            Route route = routeMapper.selectById(req.getRouteId());
            if (route == null) {
                throw new BusinessException("路线不存在: " + req.getRouteId());
            }
            if (CcConstants.REVOKED.equals(route.getStatus())) {
                throw new BusinessException("路线已撤回，不能发起询价: " + route.getRouteNo());
            }
            routeName = route.getRouteName();
        }
        if (req.getDeadline() != null && req.getDeadline().isBefore(LocalDateTime.now())) {
            throw new BusinessException("报价截止时间不能早于当前时间");
        }

        QuoteBatch batch = new QuoteBatch();
        batch.setQuoteBatchNo(generateQuoteBatchNo());
        batch.setRouteId(req.getRouteId());
        batch.setRouteNameSnap(routeName);
        batch.setZoneCode(zoneCode);
        batch.setZoneName(ZONE_NAMES.get(zoneCode));
        batch.setBoxTypeId(boxType.getId());
        batch.setBoxTypeCodeSnap(boxType.getBoxTypeCode());
        batch.setBoxTypeNameSnap(boxType.getBoxTypeName());
        batch.setServiceLevel(serviceLevel);
        batch.setServiceLevelName(SERVICE_LEVEL_NAMES.get(serviceLevel));
        batch.setExpectShipTime(req.getExpectShipTime());
        batch.setCargoDesc(req.getCargoDesc());
        batch.setDeadline(req.getDeadline());
        batch.setStatus(CcConstants.QBATCH_OPEN);
        batch.setRemark(req.getRemark());
        quoteBatchMapper.insert(batch);

        opLogService.log(CcConstants.ENT_QUOTE_BATCH, batch.getId(), null, CcConstants.ACT_QUOTE_BATCH_CREATE,
                String.format("询价批次 %s 创建：路线=%s，温区=%s，箱型=%s，服务等级=%s；多承运商可并行报价",
                        batch.getQuoteBatchNo(), routeName == null ? "门到门(未指定建档路线)" : routeName,
                        batch.getZoneName(), batch.getBoxTypeNameSnap(), batch.getServiceLevelName()));
        return batch;
    }

    /** 作废询价批次：仅 OPEN 且无锁价单可作废，报价行全部保留（状态不再变化），只追加留痕 */
    @Transactional
    public QuoteBatch voidBatch(Long quoteBatchId, String reason) {
        QuoteBatch batch = requireQuoteBatch(quoteBatchId);
        if (!CcConstants.QBATCH_OPEN.equals(batch.getStatus()) && !CcConstants.QBATCH_EXPIRED.equals(batch.getStatus())) {
            throw new BusinessException(String.format("询价批次 %s 当前状态=%s，不能作废",
                    batch.getQuoteBatchNo(), batch.getStatus()));
        }
        Long lockCount = priceLockMapper.selectCount(new QueryWrapper<PriceLock>()
                .eq("quote_batch_id", batch.getId()));
        if (lockCount != null && lockCount > 0) {
            throw new BusinessException("询价批次已产生锁价单，不能作废（如需终止请作废锁价单）");
        }
        batch.setStatus(CcConstants.QBATCH_VOIDED);
        quoteBatchMapper.updateById(batch);
        opLogService.log(CcConstants.ENT_QUOTE_BATCH, batch.getId(), null, CcConstants.ACT_QUOTE_BATCH_VOID,
                String.format("询价批次 %s 作废，全部报价行原样保留。原因：%s", batch.getQuoteBatchNo(), reason));
        return batch;
    }

    // ---------------- 承运商报价 ----------------

    @Transactional
    public CarrierQuote submitQuote(QuoteSubmitRequest req) {
        QuoteBatch batch = requireQuoteBatch(req.getQuoteBatchId());
        if (!CcConstants.QBATCH_OPEN.equals(batch.getStatus())) {
            throw new BusinessException(String.format("询价批次 %s 当前状态=%s，不再接受报价",
                    batch.getQuoteBatchNo(), batch.getStatus()));
        }
        Carrier carrier = requireActiveCarrier(req.getCarrierId());
        if (req.getValidUntil() == null || !req.getValidUntil().isAfter(LocalDateTime.now())) {
            throw new BusinessException("报价有效期必须晚于当前时间，过期/即刻失效的报价不能提交");
        }

        // 同批次同承运商至多一条当前有效（SUBMITTED）报价；撤报（WITHDRAWN）后再报为新行
        List<CarrierQuote> active = quoteMapper.selectList(new QueryWrapper<CarrierQuote>()
                .eq("quote_batch_id", batch.getId())
                .eq("carrier_id", carrier.getId())
                .eq("status", CcConstants.QUOTE_SUBMITTED));
        if (!active.isEmpty()) {
            throw new BusinessException(String.format(
                    "承运商 %s 在询价批次 %s 已有有效报价 %s，请先撤报再重新报价",
                    carrier.getCarrierName(), batch.getQuoteBatchNo(), active.get(0).getQuoteNo()));
        }

        // 金额拆分统一重算（税额/含税总价不信客户端传值）
        PriceCalculator.Breakdown breakdown = PriceCalculator.calculate(
                req.getBaseFreight(), req.getTaxRate(), req.getSurcharges());

        LocalDateTime now = LocalDateTime.now();
        CarrierQuote quote = new CarrierQuote();
        quote.setQuoteNo(generateQuoteNo());
        quote.setQuoteBatchId(batch.getId());
        quote.setCarrierId(carrier.getId());
        quote.setCarrierNameSnap(carrier.getCarrierName());
        // 维度快照（从询价批次复制）
        quote.setRouteId(batch.getRouteId());
        quote.setRouteNameSnap(batch.getRouteNameSnap());
        quote.setZoneCode(batch.getZoneCode());
        quote.setZoneName(batch.getZoneName());
        quote.setBoxTypeId(batch.getBoxTypeId());
        quote.setBoxTypeCodeSnap(batch.getBoxTypeCodeSnap());
        quote.setBoxTypeNameSnap(batch.getBoxTypeNameSnap());
        quote.setServiceLevel(batch.getServiceLevel());
        quote.setServiceLevelName(batch.getServiceLevelName());
        // 金额
        quote.setCurrency("CNY");
        quote.setBaseFreight(req.getBaseFreight());
        quote.setSurchargeTotal(breakdown.getSurchargeTotal());
        quote.setTaxRate(req.getTaxRate());
        quote.setTaxAmount(breakdown.getTaxAmount());
        quote.setTotalAmount(breakdown.getTotalAmount());
        quote.setStatus(CcConstants.QUOTE_SUBMITTED);
        quote.setValidFrom(now);
        quote.setValidUntil(req.getValidUntil());
        quote.setSubmitName(UserContext.currentName());
        quote.setQuoteRemark(req.getQuoteRemark());
        quoteMapper.insert(quote);

        // 附加费逐行留痕（只追加）
        List<SurchargeItem> items = req.getSurcharges() == null ? new ArrayList<>() : req.getSurcharges();
        for (SurchargeItem item : items) {
            QuoteSurcharge row = new QuoteSurcharge();
            row.setQuoteId(quote.getId());
            row.setFeeCode(item.getFeeCode().trim());
            row.setFeeName(item.getFeeName().trim());
            row.setAmount(item.getAmount());
            row.setFeeNote(item.getFeeNote());
            surchargeMapper.insert(row);
        }

        opLogService.log(CcConstants.ENT_QUOTE, quote.getId(), null, CcConstants.ACT_QUOTE_SUBMIT,
                String.format("承运商 %s 对询价批次 %s 提交报价 %s：基础运费=%s，附加费合计=%s（%d 笔），税率=%s，税额=%s，含税总价=%s，有效期至=%s",
                        carrier.getCarrierName(), batch.getQuoteBatchNo(), quote.getQuoteNo(),
                        quote.getBaseFreight(), quote.getSurchargeTotal(), items.size(),
                        quote.getTaxRate(), quote.getTaxAmount(), quote.getTotalAmount(), quote.getValidUntil()));
        return quote;
    }

    @Transactional
    public CarrierQuote withdraw(QuoteWithdrawRequest req) {
        CarrierQuote quote = requireQuote(req.getQuoteId());
        if (!CcConstants.QUOTE_SUBMITTED.equals(quote.getStatus())) {
            throw new BusinessException(String.format("报价 %s 当前状态=%s，仅有效报价可撤报",
                    quote.getQuoteNo(), quote.getStatus()));
        }
        if (req.getReason() == null || req.getReason().trim().isEmpty()) {
            throw new BusinessException("撤报原因不能为空");
        }
        quote.setStatus(CcConstants.QUOTE_WITHDRAWN);
        quote.setWithdrawTime(LocalDateTime.now());
        quote.setWithdrawReason(req.getReason().trim());
        quoteMapper.updateById(quote);

        opLogService.log(CcConstants.ENT_QUOTE, quote.getId(), null, CcConstants.ACT_QUOTE_WITHDRAW,
                String.format("承运商 %s 报价 %s（询价批次=%s）撤报，原报价金额行保留，可重新报价。原因：%s",
                        quote.getCarrierNameSnap(), quote.getQuoteNo(),
                        quote.getQuoteBatchId() == null ? "" : batchNo(quote.getQuoteBatchId()),
                        quote.getWithdrawReason()));
        return quote;
    }

    // ---------------- 锁价比选 ----------------

    @Transactional
    public PriceLock lock(PriceLockRequest req) {
        QuoteBatch batch = requireQuoteBatch(req.getQuoteBatchId());
        if (!CcConstants.QBATCH_OPEN.equals(batch.getStatus())) {
            throw new BusinessException(String.format("询价批次 %s 当前状态=%s，只有比选中的批次可以锁价",
                    batch.getQuoteBatchNo(), batch.getStatus()));
        }
        CarrierQuote won = requireQuote(req.getWonQuoteId());
        if (!won.getQuoteBatchId().equals(batch.getId())) {
            throw new BusinessException(String.format("中选报价 %s 不属于询价批次 %s",
                    won.getQuoteNo(), batch.getQuoteBatchNo()));
        }
        // 锁价即时判过期：过期/撤报/已落选报价都不能中选；过期报价不能被新运输批次引用
        if (CcConstants.QUOTE_EXPIRED.equals(won.getStatus()) || !won.getValidUntil().isAfter(LocalDateTime.now())) {
            // 即时把过期状态推进，拒绝中选（与定时扫描同口径）
            if (CcConstants.QUOTE_SUBMITTED.equals(won.getStatus())) {
                markQuoteExpired(won, LocalDateTime.now());
            }
            throw new BusinessException(String.format(
                    "报价 %s 已于 %s 过期，不能中选锁价、也不能被新运输批次引用",
                    won.getQuoteNo(), won.getValidUntil()));
        }
        if (!CcConstants.QUOTE_SUBMITTED.equals(won.getStatus())) {
            throw new BusinessException(String.format("中选报价 %s 当前状态=%s，仅有效（SUBMITTED）报价可中选",
                    won.getQuoteNo(), won.getStatus()));
        }
        // 数据库层 uk_cc_plock_won_quote 兜底同一报价不能被两张锁价单引用；这里给业务化提示
        Long wonLockCount = priceLockMapper.selectCount(new QueryWrapper<PriceLock>()
                .eq("won_quote_id", won.getId()));
        if (wonLockCount != null && wonLockCount > 0) {
            throw new BusinessException(String.format("报价 %s 已被锁价单中选，不能重复中选", won.getQuoteNo()));
        }

        LocalDateTime now = LocalDateTime.now();
        // 中选：WON；同批其余“仍有效”报价即时判过期后落选（LOST）
        won.setStatus(CcConstants.QUOTE_WON);
        quoteMapper.updateById(won);
        List<CarrierQuote> rivals = quoteMapper.selectList(new QueryWrapper<CarrierQuote>()
                .eq("quote_batch_id", batch.getId())
                .eq("status", CcConstants.QUOTE_SUBMITTED));
        List<String> lostNos = new ArrayList<>();
        for (CarrierQuote rival : rivals) {
            if (rival.getId().equals(won.getId())) {
                continue;
            }
            if (!rival.getValidUntil().isAfter(now)) {
                markQuoteExpired(rival, now);
            } else {
                rival.setStatus(CcConstants.QUOTE_LOST);
                quoteMapper.updateById(rival);
                lostNos.add(rival.getQuoteNo());
            }
        }

        PriceLock lock = new PriceLock();
        lock.setPriceLockNo(generatePriceLockNo());
        lock.setQuoteBatchId(batch.getId());
        lock.setWonQuoteId(won.getId());
        lock.setCarrierId(won.getCarrierId());
        lock.setCarrierNameSnap(won.getCarrierNameSnap());
        // 维度快照
        lock.setRouteId(won.getRouteId());
        lock.setRouteNameSnap(won.getRouteNameSnap());
        lock.setZoneCode(won.getZoneCode());
        lock.setZoneName(won.getZoneName());
        lock.setBoxTypeId(won.getBoxTypeId());
        lock.setBoxTypeCodeSnap(won.getBoxTypeCodeSnap());
        lock.setBoxTypeNameSnap(won.getBoxTypeNameSnap());
        lock.setServiceLevel(won.getServiceLevel());
        lock.setServiceLevelName(won.getServiceLevelName());
        // 原始报价金额快照
        lock.setCurrency(won.getCurrency());
        lock.setOrigBaseFreight(won.getBaseFreight());
        lock.setOrigSurchargeTotal(won.getSurchargeTotal());
        lock.setOrigTaxRate(won.getTaxRate());
        lock.setOrigTaxAmount(won.getTaxAmount());
        lock.setOrigTotalAmount(won.getTotalAmount());
        // 执行价初始等于原始报价（人工改价审批通过后只改 exec_* 与 price_source）
        lock.setExecBaseFreight(won.getBaseFreight());
        lock.setExecSurchargeTotal(won.getSurchargeTotal());
        lock.setExecTaxRate(won.getTaxRate());
        lock.setExecTaxAmount(won.getTaxAmount());
        lock.setExecTotalAmount(won.getTotalAmount());
        lock.setPriceSource(CcConstants.PRICE_SOURCE_QUOTE);
        lock.setStatus(CcConstants.PLOCK_LOCKED);
        lock.setLockedBy(UserContext.currentId());
        lock.setLockedName(UserContext.currentName());
        lock.setLockTime(now);
        lock.setRemark(req.getRemark());
        priceLockMapper.insert(lock);

        batch.setStatus(CcConstants.QBATCH_CLOSED);
        batch.setCloseTime(now);
        quoteBatchMapper.updateById(batch);

        opLogService.log(CcConstants.ENT_PRICE_LOCK, lock.getId(), null, CcConstants.ACT_PRICE_LOCK,
                String.format("询价批次 %s 锁价：唯一中选=%s 承运商 %s，含税总价=%s（基础运费=%s，附加费=%s，税率=%s，税额=%s）；"
                                + "其余有效报价落选 %s；中选报价全字段已快照到锁价单 %s，此后报价过期与再报价均与本单脱钩",
                        batch.getQuoteBatchNo(), won.getQuoteNo(), won.getCarrierNameSnap(),
                        won.getTotalAmount(), won.getBaseFreight(), won.getSurchargeTotal(),
                        won.getTaxRate(), won.getTaxAmount(),
                        lostNos.isEmpty() ? "无" : lostNos.toString(), lock.getPriceLockNo()));
        return lock;
    }

    /**
     * 作废锁价单（仅未消费、非改价审批中）：锁价单置 VOIDED，询价批次回到 OPEN 接受新报价；
     * 原中选/落选报价行保持终态不改写（重新锁价须从新报价行中选），全程只追加留痕。
     */
    @Transactional
    public PriceLock voidLock(Long priceLockId, String reason) {
        PriceLock lock = requirePriceLock(priceLockId);
        if (CcConstants.PLOCK_CONSUMED.equals(lock.getStatus()) || CcConstants.PLOCK_VOIDED.equals(lock.getStatus())) {
            throw new BusinessException(String.format("锁价单 %s 当前状态=%s，不能作废",
                    lock.getPriceLockNo(), lock.getStatus()));
        }
        if (CcConstants.PLOCK_PRICE_PENDING.equals(lock.getStatus())) {
            throw new BusinessException(String.format(
                    "锁价单 %s 正在人工改价审批中，请先完成审批（同意/驳回）再作废", lock.getPriceLockNo()));
        }
        lock.setStatus(CcConstants.PLOCK_VOIDED);
        lock.setVoidTime(LocalDateTime.now());
        lock.setVoidReason(reason);
        priceLockMapper.updateById(lock);

        QuoteBatch batch = requireQuoteBatch(lock.getQuoteBatchId());
        batch.setStatus(CcConstants.QBATCH_OPEN);
        batch.setCloseTime(null);
        quoteBatchMapper.updateById(batch);

        opLogService.log(CcConstants.ENT_PRICE_LOCK, lock.getId(), null, CcConstants.ACT_PRICE_LOCK_VOID,
                String.format("锁价单 %s 作废（中选原报价=%s，承运商=%s，原始快照保留），询价批次 %s 重新开放接受新报价。原因：%s",
                        lock.getPriceLockNo(), lock.getWonQuoteId(), lock.getCarrierNameSnap(),
                        batch.getQuoteBatchNo(), reason));
        return lock;
    }

    /**
     * 派车消费锁价单（由 TransportBatchService 派车事务内调用）：
     * 仅 LOCKED 可消费（PRICE_PENDING 改价审批中拦截）；消费后锁价单置 CONSUMED，
     * 此后锁价单与原报价状态完全脱钩，金额一律以快照/执行价为准。
     */
    @Transactional
    public PriceLock consumeForDispatch(Long priceLockId, Long transportBatchId) {
        PriceLock lock = requirePriceLock(priceLockId);
        if (CcConstants.PLOCK_CONSUMED.equals(lock.getStatus())) {
            if (lock.getConsumedBatchId() != null && !lock.getConsumedBatchId().equals(transportBatchId)) {
                throw new BusinessException(String.format(
                        "锁价单 %s 已被运输批次 %d 消费，一个锁价方案只能用于一个运输批次",
                        lock.getPriceLockNo(), lock.getConsumedBatchId()));
            }
            return lock;
        }
        if (CcConstants.PLOCK_VOIDED.equals(lock.getStatus())) {
            throw new BusinessException("锁价单 " + lock.getPriceLockNo() + " 已作废，不能用于派车");
        }
        if (CcConstants.PLOCK_PRICE_PENDING.equals(lock.getStatus())) {
            throw new BusinessException(String.format(
                    "锁价单 %s 的人工改价申请尚未审批完成，审批同意/驳回前不能派车引用", lock.getPriceLockNo()));
        }
        if (!CcConstants.PLOCK_LOCKED.equals(lock.getStatus())) {
            throw new BusinessException("锁价单当前状态不允许派车消费: " + lock.getStatus());
        }
        lock.setStatus(CcConstants.PLOCK_CONSUMED);
        lock.setConsumedBatchId(transportBatchId);
        lock.setConsumeTime(LocalDateTime.now());
        priceLockMapper.updateById(lock);

        opLogService.log(CcConstants.ENT_PRICE_LOCK, lock.getId(), transportBatchId, CcConstants.ACT_PRICE_LOCK_CONSUME,
                String.format("锁价单 %s 被运输批次ID=%d 派车消费：承运商=%s，执行价来源=%s，执行含税总价=%s；"
                                + "中选报价此后过期/变更均与本批脱钩，金额以锁价单快照/执行价为准",
                        lock.getPriceLockNo(), transportBatchId, lock.getCarrierNameSnap(),
                        lock.getPriceSource(), lock.getExecTotalAmount()));
        return lock;
    }

    // ---------------- 过期扫描 ----------------

    /**
     * 定时/手动扫描：把所有 valid_until 已过的 SUBMITTED 报价幂等推进为 EXPIRED（不改金额、不删行）；
     * 一个 OPEN 询价批次已无任何 SUBMITTED 报价（全部撤报/过期/从未报价且超过截止时间）时推进为 EXPIRED。
     */
    @Transactional
    public Map<String, Object> scanExpiry(LocalDateTime now) {
        List<CarrierQuote> submitted = quoteMapper.selectList(new QueryWrapper<CarrierQuote>()
                .eq("status", CcConstants.QUOTE_SUBMITTED));
        int expiredQuotes = 0;
        java.util.Set<Long> batchIds = new java.util.TreeSet<>();
        for (CarrierQuote quote : submitted) {
            if (!quote.getValidUntil().isAfter(now)) {
                markQuoteExpired(quote, now);
                expiredQuotes++;
                batchIds.add(quote.getQuoteBatchId());
            }
        }
        List<QuoteBatch> openBatches = quoteBatchMapper.selectList(new QueryWrapper<QuoteBatch>()
                .eq("status", CcConstants.QBATCH_OPEN));
        int expiredBatches = 0;
        for (QuoteBatch batch : openBatches) {
            if (isBatchDead(batch, now)) {
                batch.setStatus(CcConstants.QBATCH_EXPIRED);
                quoteBatchMapper.updateById(batch);
                expiredBatches++;
                opLogService.log(CcConstants.ENT_QUOTE_BATCH, batch.getId(), null, CcConstants.ACT_QUOTE_EXPIRE,
                        String.format("询价批次 %s 已无有效报价（截止=%s），比选按过期处理；历史报价行全部保留",
                                batch.getQuoteBatchNo(), batch.getDeadline()));
            }
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("expiredQuotes", expiredQuotes);
        summary.put("expiredBatches", expiredBatches);
        summary.put("scanTime", now);
        return summary;
    }

    /** OPEN 批次是否已“死亡”：存在截止时间且已过，并且没有任何 SUBMITTED 报价 */
    private boolean isBatchDead(QuoteBatch batch, LocalDateTime now) {
        Long activeCount = quoteMapper.selectCount(new QueryWrapper<CarrierQuote>()
                .eq("quote_batch_id", batch.getId())
                .eq("status", CcConstants.QUOTE_SUBMITTED));
        if (activeCount != null && activeCount > 0) {
            return false;
        }
        return batch.getDeadline() != null && !batch.getDeadline().isAfter(now);
    }

    private void markQuoteExpired(CarrierQuote quote, LocalDateTime now) {
        quote.setStatus(CcConstants.QUOTE_EXPIRED);
        quote.setExpireTime(now);
        quoteMapper.updateById(quote);
        opLogService.log(CcConstants.ENT_QUOTE, quote.getId(), null, CcConstants.ACT_QUOTE_EXPIRE,
                String.format("报价 %s（承运商=%s，有效期至=%s）过期，金额行原样保留；过期后不能锁价、不能被新运输批次引用",
                        quote.getQuoteNo(), quote.getCarrierNameSnap(), quote.getValidUntil()));
    }

    // ---------------- 查询 / 比选视图 / 反查 ----------------

    public QuoteBatch requireQuoteBatch(Long id) {
        QuoteBatch batch = quoteBatchMapper.selectById(id);
        if (batch == null) {
            throw new BusinessException("询价批次不存在: " + id);
        }
        return batch;
    }

    public CarrierQuote requireQuote(Long id) {
        CarrierQuote quote = quoteMapper.selectById(id);
        if (quote == null) {
            throw new BusinessException("承运商报价不存在: " + id);
        }
        return quote;
    }

    public PriceLock requirePriceLock(Long id) {
        PriceLock lock = priceLockMapper.selectById(id);
        if (lock == null) {
            throw new BusinessException("锁价单不存在: " + id);
        }
        return lock;
    }

    private Carrier requireActiveCarrier(Long id) {
        Carrier carrier = carrierMapper.selectById(id);
        if (carrier == null) {
            throw new BusinessException("承运商不存在: " + id);
        }
        if (CcConstants.REVOKED.equals(carrier.getStatus())) {
            throw new BusinessException("承运商已撤回，不能参与报价: " + carrier.getCarrierNo());
        }
        return carrier;
    }

    public List<QuoteSurcharge> listSurcharges(Long quoteId) {
        return surchargeMapper.selectList(new QueryWrapper<QuoteSurcharge>()
                .eq("quote_id", quoteId).orderByAsc("id"));
    }

    /** 比选视图：询价批次 + 全部报价（含附加费明细），按含税总价升序便于比选 */
    public Map<String, Object> comparison(Long quoteBatchId) {
        QuoteBatch batch = requireQuoteBatch(quoteBatchId);
        List<CarrierQuote> quotes = quoteMapper.selectList(new QueryWrapper<CarrierQuote>()
                .eq("quote_batch_id", batch.getId()).orderByAsc("total_amount").orderByAsc("id"));
        List<Map<String, Object>> quoteList = new ArrayList<>();
        for (CarrierQuote quote : quotes) {
            Map<String, Object> q = new LinkedHashMap<>();
            q.put("quote", quote);
            q.put("surcharges", listSurcharges(quote.getId()));
            quoteList.add(q);
        }
        List<PriceLock> locks = priceLockMapper.selectList(new QueryWrapper<PriceLock>()
                .eq("quote_batch_id", batch.getId()).orderByAsc("id"));
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("quoteBatch", batch);
        view.put("quotes", quoteList);
        view.put("priceLocks", locks);
        return view;
    }

    public List<QuoteBatch> listOpenBatches() {
        return quoteBatchMapper.selectList(new QueryWrapper<QuoteBatch>()
                .in("status", Arrays.asList(CcConstants.QBATCH_OPEN, CcConstants.QBATCH_EXPIRED))
                .orderByDesc("id"));
    }

    private String batchNo(Long batchId) {
        QuoteBatch batch = quoteBatchMapper.selectById(batchId);
        return batch == null ? String.valueOf(batchId) : batch.getQuoteBatchNo();
    }

    private String normalizeZone(String zoneCode) {
        if (zoneCode == null || !ZONE_NAMES.containsKey(zoneCode.trim().toUpperCase())) {
            throw new BusinessException("不支持的温区代码: " + zoneCode
                    + "，可选 REFRIGERATE/FROZEN/DEEP_FROZEN/AMBIENT");
        }
        return zoneCode.trim().toUpperCase();
    }

    private String normalizeServiceLevel(String level) {
        if (level == null || !SERVICE_LEVEL_NAMES.containsKey(level.trim().toUpperCase())) {
            throw new BusinessException("不支持的服务等级: " + level
                    + "，可选 STANDARD/EXPRESS/DEDICATED");
        }
        return level.trim().toUpperCase();
    }

    private String generateQuoteBatchNo() {
        return String.format("XJ%09d", sequenceMapper.nextQuoteBatchNo());
    }

    private String generateQuoteNo() {
        return String.format("BJ%09d", sequenceMapper.nextQuoteNo());
    }

    private String generatePriceLockNo() {
        return String.format("SJ%09d", sequenceMapper.nextPriceLockNo());
    }
}

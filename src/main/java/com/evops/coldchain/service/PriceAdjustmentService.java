package com.evops.coldchain.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.coldchain.CcConstants;
import com.evops.coldchain.dto.PriceAdjustmentApplyRequest;
import com.evops.coldchain.dto.SurchargeItem;
import com.evops.coldchain.entity.PriceAdjustment;
import com.evops.coldchain.entity.PriceAdjustmentSurcharge;
import com.evops.coldchain.entity.PriceLock;
import com.evops.coldchain.mapper.PriceAdjustmentMapper;
import com.evops.coldchain.mapper.PriceAdjustmentSurchargeMapper;
import com.evops.coldchain.mapper.PriceLockMapper;
import com.evops.coldchain.mapper.SequenceMapper;
import com.evops.common.BusinessException;
import com.evops.common.UserContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 人工改价审批服务（V8，两步流程）。
 *
 * - 申请：锁价单必须为 LOCKED（已消费/已作废/审批中不可再申请）；落 PENDING 审批单，锁价单进入 PRICE_PENDING，
 *   派车消费卡口据此拦截“审批半成品”。
 * - 同意：把改后金额写入锁价单 exec_* 执行价列，price_source=ADJUSTED；orig_* 原始报价快照列原值永不动。
 * - 驳回：不改执行价，锁价单回到 LOCKED 可继续按原报价派车。
 * - 改后附加费明细逐行落 t_cc_price_adjustment_surcharge（只追加），审批留痕在 t_cc_op_log。
 */
@Service
public class PriceAdjustmentService {

    @Resource
    private PriceAdjustmentMapper adjustmentMapper;
    @Resource
    private PriceAdjustmentSurchargeMapper adjustmentSurchargeMapper;
    @Resource
    private PriceLockMapper priceLockMapper;
    @Resource
    private SequenceMapper sequenceMapper;
    @Resource
    private QuoteBatchService quoteBatchService;
    @Resource
    private OpLogService opLogService;

    @Transactional
    public PriceAdjustment apply(PriceAdjustmentApplyRequest req) {
        PriceLock lock = quoteBatchService.requirePriceLock(req.getPriceLockId());
        if (CcConstants.PLOCK_CONSUMED.equals(lock.getStatus())) {
            throw new BusinessException(String.format(
                    "锁价单 %s 已被运输批次消费，价格已随派车固化，不能再人工改价", lock.getPriceLockNo()));
        }
        if (CcConstants.PLOCK_VOIDED.equals(lock.getStatus())) {
            throw new BusinessException("锁价单 " + lock.getPriceLockNo() + " 已作废，不能改价");
        }
        if (CcConstants.PLOCK_PRICE_PENDING.equals(lock.getStatus())) {
            PriceAdjustment pending = latestByLock(lock.getId());
            throw new BusinessException(String.format(
                    "锁价单 %s 已有人工改价申请 %s 停留待审批，不能重复申请，请等待同意/驳回",
                    lock.getPriceLockNo(), pending == null ? "" : pending.getAdjustmentNo()));
        }
        if (!CcConstants.PLOCK_LOCKED.equals(lock.getStatus())) {
            throw new BusinessException("锁价单当前状态不允许申请改价: " + lock.getStatus());
        }

        // 改后金额同样拆分重算（税额/总价不信客户端）
        PriceCalculator.Breakdown breakdown = PriceCalculator.calculate(
                req.getBaseFreight(), req.getTaxRate(), req.getSurcharges());

        LocalDateTime now = LocalDateTime.now();
        PriceAdjustment adjustment = new PriceAdjustment();
        adjustment.setAdjustmentNo(generateAdjustmentNo());
        adjustment.setPriceLockId(lock.getId());
        adjustment.setAdjBaseFreight(req.getBaseFreight());
        adjustment.setAdjSurchargeTotal(breakdown.getSurchargeTotal());
        adjustment.setAdjTaxRate(req.getTaxRate());
        adjustment.setAdjTaxAmount(breakdown.getTaxAmount());
        adjustment.setAdjTotalAmount(breakdown.getTotalAmount());
        adjustment.setChangeReason(req.getChangeReason().trim());
        adjustment.setStatus(CcConstants.ADJUST_PENDING);
        adjustment.setApplyBy(UserContext.currentId());
        adjustment.setApplyName(UserContext.currentName());
        adjustment.setApplyTime(now);
        adjustmentMapper.insert(adjustment);

        List<SurchargeItem> items = req.getSurcharges() == null ? new ArrayList<>() : req.getSurcharges();
        for (SurchargeItem item : items) {
            PriceAdjustmentSurcharge row = new PriceAdjustmentSurcharge();
            row.setAdjustmentId(adjustment.getId());
            row.setFeeCode(item.getFeeCode().trim());
            row.setFeeName(item.getFeeName().trim());
            row.setAmount(item.getAmount());
            row.setFeeNote(item.getFeeNote());
            adjustmentSurchargeMapper.insert(row);
        }

        lock.setStatus(CcConstants.PLOCK_PRICE_PENDING);
        priceLockMapper.updateById(lock);

        opLogService.log(CcConstants.ENT_PRICE_ADJUSTMENT, adjustment.getId(), null,
                CcConstants.ACT_PRICE_ADJUST_APPLY,
                String.format("锁价单 %s 人工改价申请 %s 停留待审批：原含税总价=%s（基础=%s/附加=%s/税率=%s/税额=%s）"
                                + " -> 改后含税总价=%s（基础=%s/附加=%s/税率=%s/税额=%s，附加费 %d 笔）；"
                                + "审批同意前锁价单不得派车消费，原始报价快照列保留不动。事由：%s；申请人=%s",
                        lock.getPriceLockNo(), adjustment.getAdjustmentNo(),
                        lock.getOrigTotalAmount(), lock.getOrigBaseFreight(), lock.getOrigSurchargeTotal(),
                        lock.getOrigTaxRate(), lock.getOrigTaxAmount(),
                        adjustment.getAdjTotalAmount(), adjustment.getAdjBaseFreight(),
                        adjustment.getAdjSurchargeTotal(), adjustment.getAdjTaxRate(), adjustment.getAdjTaxAmount(),
                        items.size(), adjustment.getChangeReason(), adjustment.getApplyName()));
        return adjustment;
    }

    @Transactional
    public PriceAdjustment approve(Long adjustmentId, String reviewNote) {
        return review(adjustmentId, true, reviewNote);
    }

    @Transactional
    public PriceAdjustment reject(Long adjustmentId, String reviewNote) {
        return review(adjustmentId, false, reviewNote);
    }

    private PriceAdjustment review(Long adjustmentId, boolean approved, String reviewNote) {
        PriceAdjustment adjustment = requireAdjustment(adjustmentId);
        if (!CcConstants.ADJUST_PENDING.equals(adjustment.getStatus())) {
            throw new BusinessException(String.format("改价审批单 %s 当前状态=%s，仅待审批申请可审查",
                    adjustment.getAdjustmentNo(), adjustment.getStatus()));
        }
        PriceLock lock = quoteBatchService.requirePriceLock(adjustment.getPriceLockId());
        LocalDateTime now = LocalDateTime.now();

        adjustment.setReviewBy(UserContext.currentId());
        adjustment.setReviewName(UserContext.currentName());
        adjustment.setReviewTime(now);
        adjustment.setReviewNote(reviewNote.trim());

        if (approved) {
            if (CcConstants.PLOCK_CONSUMED.equals(lock.getStatus()) || CcConstants.PLOCK_VOIDED.equals(lock.getStatus())) {
                throw new BusinessException(String.format(
                        "锁价单 %s 已%s，改价审批不能同意", lock.getPriceLockNo(),
                        CcConstants.PLOCK_CONSUMED.equals(lock.getStatus()) ? "被运输批次消费" : "作废"));
            }
            adjustment.setStatus(CcConstants.ADJUST_APPROVED);
            // 只写执行价列；orig_* 原始报价快照列保持原值
            lock.setExecBaseFreight(adjustment.getAdjBaseFreight());
            lock.setExecSurchargeTotal(adjustment.getAdjSurchargeTotal());
            lock.setExecTaxRate(adjustment.getAdjTaxRate());
            lock.setExecTaxAmount(adjustment.getAdjTaxAmount());
            lock.setExecTotalAmount(adjustment.getAdjTotalAmount());
            lock.setPriceSource(CcConstants.PRICE_SOURCE_ADJUSTED);
            lock.setStatus(CcConstants.PLOCK_LOCKED);
        } else {
            adjustment.setStatus(CcConstants.ADJUST_REJECTED);
            // 驳回：执行价维持原报价，锁价单回到 LOCKED 可按原报价派车
            lock.setStatus(CcConstants.PLOCK_LOCKED);
        }
        adjustmentMapper.updateById(adjustment);
        priceLockMapper.updateById(lock);

        opLogService.log(CcConstants.ENT_PRICE_ADJUSTMENT, adjustment.getId(), null,
                approved ? CcConstants.ACT_PRICE_ADJUST_APPROVE : CcConstants.ACT_PRICE_ADJUST_REJECT,
                String.format("改价审批单 %s（锁价单=%s）%s：审批人=%s，意见=%s；%s",
                        adjustment.getAdjustmentNo(), lock.getPriceLockNo(),
                        approved ? "同意，执行价已更新为改后价" : "驳回，锁价单恢复按原始报价执行",
                        adjustment.getReviewName(), adjustment.getReviewNote(),
                        approved ? String.format("原始报价含税总价=%s 快照保留，执行含税总价=%s，价格来源=ADJUSTED",
                                lock.getOrigTotalAmount(), lock.getExecTotalAmount())
                                : String.format("执行含税总价仍为原始报价 %s", lock.getExecTotalAmount())));
        return adjustment;
    }

    public PriceAdjustment requireAdjustment(Long id) {
        PriceAdjustment adjustment = adjustmentMapper.selectById(id);
        if (adjustment == null) {
            throw new BusinessException("人工改价审批单不存在: " + id);
        }
        return adjustment;
    }

    public PriceAdjustment latestByLock(Long priceLockId) {
        List<PriceAdjustment> rows = adjustmentMapper.selectList(new QueryWrapper<PriceAdjustment>()
                .eq("price_lock_id", priceLockId).orderByDesc("id"));
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<PriceAdjustment> listByLock(Long priceLockId) {
        return adjustmentMapper.selectList(new QueryWrapper<PriceAdjustment>()
                .eq("price_lock_id", priceLockId).orderByAsc("id"));
    }

    public List<PriceAdjustmentSurcharge> listAdjustmentSurcharges(Long adjustmentId) {
        return adjustmentSurchargeMapper.selectList(new QueryWrapper<PriceAdjustmentSurcharge>()
                .eq("adjustment_id", adjustmentId).orderByAsc("id"));
    }

    public List<PriceAdjustment> listPending() {
        return adjustmentMapper.selectList(new QueryWrapper<PriceAdjustment>()
                .eq("status", CcConstants.ADJUST_PENDING).orderByAsc("id"));
    }

    private String generateAdjustmentNo() {
        return String.format("GJ%09d", sequenceMapper.nextAdjustmentNo());
    }
}

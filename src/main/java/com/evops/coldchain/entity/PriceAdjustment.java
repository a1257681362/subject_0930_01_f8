package com.evops.coldchain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 人工改价审批单（V8，两步）：申请 PENDING -> 同意 APPROVED（执行价生效）/ 驳回 REJECTED（锁价单回 LOCKED）。
 * 改后金额按报价同口径拆分（基础运费/附加费合计/税率/税额/总价，总价服务层重算），
 * 本单只表达“改后价”，锁价单 orig_* 原始报价快照永不被本单修改。
 */
@TableName("t_cc_price_adjustment")
public class PriceAdjustment extends BaseEntity {
    private String adjustmentNo;
    private Long priceLockId;
    private BigDecimal adjBaseFreight;
    private BigDecimal adjSurchargeTotal;
    private BigDecimal adjTaxRate;
    private BigDecimal adjTaxAmount;
    private BigDecimal adjTotalAmount;
    private String changeReason;
    /** PENDING / APPROVED / REJECTED */
    private String status;
    private Long applyBy;
    private String applyName;
    private LocalDateTime applyTime;
    private Long reviewBy;
    private String reviewName;
    private LocalDateTime reviewTime;
    private String reviewNote;

    public String getAdjustmentNo() { return adjustmentNo; }
    public void setAdjustmentNo(String adjustmentNo) { this.adjustmentNo = adjustmentNo; }
    public Long getPriceLockId() { return priceLockId; }
    public void setPriceLockId(Long priceLockId) { this.priceLockId = priceLockId; }
    public BigDecimal getAdjBaseFreight() { return adjBaseFreight; }
    public void setAdjBaseFreight(BigDecimal adjBaseFreight) { this.adjBaseFreight = adjBaseFreight; }
    public BigDecimal getAdjSurchargeTotal() { return adjSurchargeTotal; }
    public void setAdjSurchargeTotal(BigDecimal adjSurchargeTotal) { this.adjSurchargeTotal = adjSurchargeTotal; }
    public BigDecimal getAdjTaxRate() { return adjTaxRate; }
    public void setAdjTaxRate(BigDecimal adjTaxRate) { this.adjTaxRate = adjTaxRate; }
    public BigDecimal getAdjTaxAmount() { return adjTaxAmount; }
    public void setAdjTaxAmount(BigDecimal adjTaxAmount) { this.adjTaxAmount = adjTaxAmount; }
    public BigDecimal getAdjTotalAmount() { return adjTotalAmount; }
    public void setAdjTotalAmount(BigDecimal adjTotalAmount) { this.adjTotalAmount = adjTotalAmount; }
    public String getChangeReason() { return changeReason; }
    public void setChangeReason(String changeReason) { this.changeReason = changeReason; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getApplyBy() { return applyBy; }
    public void setApplyBy(Long applyBy) { this.applyBy = applyBy; }
    public String getApplyName() { return applyName; }
    public void setApplyName(String applyName) { this.applyName = applyName; }
    public LocalDateTime getApplyTime() { return applyTime; }
    public void setApplyTime(LocalDateTime applyTime) { this.applyTime = applyTime; }
    public Long getReviewBy() { return reviewBy; }
    public void setReviewBy(Long reviewBy) { this.reviewBy = reviewBy; }
    public String getReviewName() { return reviewName; }
    public void setReviewName(String reviewName) { this.reviewName = reviewName; }
    public LocalDateTime getReviewTime() { return reviewTime; }
    public void setReviewTime(LocalDateTime reviewTime) { this.reviewTime = reviewTime; }
    public String getReviewNote() { return reviewNote; }
    public void setReviewNote(String reviewNote) { this.reviewNote = reviewNote; }
}

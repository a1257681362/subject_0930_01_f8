package com.evops.coldchain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 锁价单（V8）。一次报价比选只能中选唯一一个有效方案：
 * 中选报价的四个维度与原始金额（orig_*）逐字段快照到本表，此后报价过期/再报价均与本单脱钩。
 *
 * 人工改价不覆盖原报价：审批同意后只改 exec_* 执行价列并把 price_source 置 ADJUSTED，
 * orig_* 列原值永不动。
 *
 * 状态：LOCKED 已锁定待派车 / PRICE_PENDING 改价审批中 / CONSUMED 已派车消费 / VOIDED 作废。
 */
@TableName("t_cc_price_lock")
public class PriceLock extends BaseEntity {
    private String priceLockNo;
    private Long quoteBatchId;
    private Long wonQuoteId;
    private Long carrierId;
    private String carrierNameSnap;
    // 维度快照
    private Long routeId;
    private String routeNameSnap;
    private String zoneCode;
    private String zoneName;
    private Long boxTypeId;
    private String boxTypeCodeSnap;
    private String boxTypeNameSnap;
    private String serviceLevel;
    private String serviceLevelName;
    // 原始报价金额快照
    private String currency;
    private BigDecimal origBaseFreight;
    private BigDecimal origSurchargeTotal;
    private BigDecimal origTaxRate;
    private BigDecimal origTaxAmount;
    private BigDecimal origTotalAmount;
    // 执行价（锁价时=原价；审批改价后为改后价）
    private BigDecimal execBaseFreight;
    private BigDecimal execSurchargeTotal;
    private BigDecimal execTaxRate;
    private BigDecimal execTaxAmount;
    private BigDecimal execTotalAmount;
    private String priceSource;
    // 状态与处理人
    private String status;
    private Long lockedBy;
    private String lockedName;
    private LocalDateTime lockTime;
    private Long consumedBatchId;
    private LocalDateTime consumeTime;
    private LocalDateTime voidTime;
    private String voidReason;
    private String remark;

    public String getPriceLockNo() { return priceLockNo; }
    public void setPriceLockNo(String priceLockNo) { this.priceLockNo = priceLockNo; }
    public Long getQuoteBatchId() { return quoteBatchId; }
    public void setQuoteBatchId(Long quoteBatchId) { this.quoteBatchId = quoteBatchId; }
    public Long getWonQuoteId() { return wonQuoteId; }
    public void setWonQuoteId(Long wonQuoteId) { this.wonQuoteId = wonQuoteId; }
    public Long getCarrierId() { return carrierId; }
    public void setCarrierId(Long carrierId) { this.carrierId = carrierId; }
    public String getCarrierNameSnap() { return carrierNameSnap; }
    public void setCarrierNameSnap(String carrierNameSnap) { this.carrierNameSnap = carrierNameSnap; }
    public Long getRouteId() { return routeId; }
    public void setRouteId(Long routeId) { this.routeId = routeId; }
    public String getRouteNameSnap() { return routeNameSnap; }
    public void setRouteNameSnap(String routeNameSnap) { this.routeNameSnap = routeNameSnap; }
    public String getZoneCode() { return zoneCode; }
    public void setZoneCode(String zoneCode) { this.zoneCode = zoneCode; }
    public String getZoneName() { return zoneName; }
    public void setZoneName(String zoneName) { this.zoneName = zoneName; }
    public Long getBoxTypeId() { return boxTypeId; }
    public void setBoxTypeId(Long boxTypeId) { this.boxTypeId = boxTypeId; }
    public String getBoxTypeCodeSnap() { return boxTypeCodeSnap; }
    public void setBoxTypeCodeSnap(String boxTypeCodeSnap) { this.boxTypeCodeSnap = boxTypeCodeSnap; }
    public String getBoxTypeNameSnap() { return boxTypeNameSnap; }
    public void setBoxTypeNameSnap(String boxTypeNameSnap) { this.boxTypeNameSnap = boxTypeNameSnap; }
    public String getServiceLevel() { return serviceLevel; }
    public void setServiceLevel(String serviceLevel) { this.serviceLevel = serviceLevel; }
    public String getServiceLevelName() { return serviceLevelName; }
    public void setServiceLevelName(String serviceLevelName) { this.serviceLevelName = serviceLevelName; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public BigDecimal getOrigBaseFreight() { return origBaseFreight; }
    public void setOrigBaseFreight(BigDecimal origBaseFreight) { this.origBaseFreight = origBaseFreight; }
    public BigDecimal getOrigSurchargeTotal() { return origSurchargeTotal; }
    public void setOrigSurchargeTotal(BigDecimal origSurchargeTotal) { this.origSurchargeTotal = origSurchargeTotal; }
    public BigDecimal getOrigTaxRate() { return origTaxRate; }
    public void setOrigTaxRate(BigDecimal origTaxRate) { this.origTaxRate = origTaxRate; }
    public BigDecimal getOrigTaxAmount() { return origTaxAmount; }
    public void setOrigTaxAmount(BigDecimal origTaxAmount) { this.origTaxAmount = origTaxAmount; }
    public BigDecimal getOrigTotalAmount() { return origTotalAmount; }
    public void setOrigTotalAmount(BigDecimal origTotalAmount) { this.origTotalAmount = origTotalAmount; }
    public BigDecimal getExecBaseFreight() { return execBaseFreight; }
    public void setExecBaseFreight(BigDecimal execBaseFreight) { this.execBaseFreight = execBaseFreight; }
    public BigDecimal getExecSurchargeTotal() { return execSurchargeTotal; }
    public void setExecSurchargeTotal(BigDecimal execSurchargeTotal) { this.execSurchargeTotal = execSurchargeTotal; }
    public BigDecimal getExecTaxRate() { return execTaxRate; }
    public void setExecTaxRate(BigDecimal execTaxRate) { this.execTaxRate = execTaxRate; }
    public BigDecimal getExecTaxAmount() { return execTaxAmount; }
    public void setExecTaxAmount(BigDecimal execTaxAmount) { this.execTaxAmount = execTaxAmount; }
    public BigDecimal getExecTotalAmount() { return execTotalAmount; }
    public void setExecTotalAmount(BigDecimal execTotalAmount) { this.execTotalAmount = execTotalAmount; }
    public String getPriceSource() { return priceSource; }
    public void setPriceSource(String priceSource) { this.priceSource = priceSource; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getLockedBy() { return lockedBy; }
    public void setLockedBy(Long lockedBy) { this.lockedBy = lockedBy; }
    public String getLockedName() { return lockedName; }
    public void setLockedName(String lockedName) { this.lockedName = lockedName; }
    public LocalDateTime getLockTime() { return lockTime; }
    public void setLockTime(LocalDateTime lockTime) { this.lockTime = lockTime; }
    public Long getConsumedBatchId() { return consumedBatchId; }
    public void setConsumedBatchId(Long consumedBatchId) { this.consumedBatchId = consumedBatchId; }
    public LocalDateTime getConsumeTime() { return consumeTime; }
    public void setConsumeTime(LocalDateTime consumeTime) { this.consumeTime = consumeTime; }
    public LocalDateTime getVoidTime() { return voidTime; }
    public void setVoidTime(LocalDateTime voidTime) { this.voidTime = voidTime; }
    public String getVoidReason() { return voidReason; }
    public void setVoidReason(String voidReason) { this.voidReason = voidReason; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}

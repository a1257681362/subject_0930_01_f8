package com.evops.coldchain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 承运商报价（V8）。多个承运商可对同一询价批次并行报价；同批次同承运商的“当前有效”
 * 报价至多一条（撤报置 WITHDRAWN 后可再报新行）。
 *
 * 金额拆分：total_amount = base_freight + surcharge_total + tax_amount，
 * tax_amount = (base_freight + surcharge_total) × tax_rate，全部由服务层重算。
 *
 * 状态：SUBMITTED 有效中 / WITHDRAWN 撤报 / EXPIRED 过期 / WON 中选 / LOST 未中选。
 * 报价行只推进状态，不删除、不修改金额；中选后被锁价单快照引用。
 */
@TableName("t_cc_carrier_quote")
public class CarrierQuote extends BaseEntity {
    private String quoteNo;
    private Long quoteBatchId;
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
    // 金额拆分
    private String currency;
    private BigDecimal baseFreight;
    private BigDecimal surchargeTotal;
    private BigDecimal taxRate;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    // 状态与有效期
    private String status;
    private LocalDateTime validFrom;
    private LocalDateTime validUntil;
    private String submitName;
    private LocalDateTime withdrawTime;
    private String withdrawReason;
    private LocalDateTime expireTime;
    private String quoteRemark;

    public String getQuoteNo() { return quoteNo; }
    public void setQuoteNo(String quoteNo) { this.quoteNo = quoteNo; }
    public Long getQuoteBatchId() { return quoteBatchId; }
    public void setQuoteBatchId(Long quoteBatchId) { this.quoteBatchId = quoteBatchId; }
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
    public BigDecimal getBaseFreight() { return baseFreight; }
    public void setBaseFreight(BigDecimal baseFreight) { this.baseFreight = baseFreight; }
    public BigDecimal getSurchargeTotal() { return surchargeTotal; }
    public void setSurchargeTotal(BigDecimal surchargeTotal) { this.surchargeTotal = surchargeTotal; }
    public BigDecimal getTaxRate() { return taxRate; }
    public void setTaxRate(BigDecimal taxRate) { this.taxRate = taxRate; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public void setTaxAmount(BigDecimal taxAmount) { this.taxAmount = taxAmount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getValidFrom() { return validFrom; }
    public void setValidFrom(LocalDateTime validFrom) { this.validFrom = validFrom; }
    public LocalDateTime getValidUntil() { return validUntil; }
    public void setValidUntil(LocalDateTime validUntil) { this.validUntil = validUntil; }
    public String getSubmitName() { return submitName; }
    public void setSubmitName(String submitName) { this.submitName = submitName; }
    public LocalDateTime getWithdrawTime() { return withdrawTime; }
    public void setWithdrawTime(LocalDateTime withdrawTime) { this.withdrawTime = withdrawTime; }
    public String getWithdrawReason() { return withdrawReason; }
    public void setWithdrawReason(String withdrawReason) { this.withdrawReason = withdrawReason; }
    public LocalDateTime getExpireTime() { return expireTime; }
    public void setExpireTime(LocalDateTime expireTime) { this.expireTime = expireTime; }
    public String getQuoteRemark() { return quoteRemark; }
    public void setQuoteRemark(String quoteRemark) { this.quoteRemark = quoteRemark; }
}

package com.evops.coldchain.dto;

import javax.validation.Valid;
import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 承运商报价提交请求（V8）。
 * 金额只接收“基础运费 + 附加费明细 + 税率”，税额与含税总价一律由服务层重算，
 * 防止客户端传入不拆分金额或与明细不一致的总价。
 */
public class QuoteSubmitRequest {
    @NotNull(message = "询价批次ID不能为空")
    private Long quoteBatchId;
    @NotNull(message = "承运商不能为空")
    private Long carrierId;
    @NotNull(message = "基础运费不能为空")
    @DecimalMin(value = "0", message = "基础运费不能为负")
    private BigDecimal baseFreight;
    /** 税率，0~1 之间（不含 1），如 0.09 表示 9% */
    @NotNull(message = "税率不能为空")
    @DecimalMin(value = "0", message = "税率不能为负")
    @DecimalMax(value = "0.9999", message = "税率必须小于 1")
    private BigDecimal taxRate;
    /** 附加费明细（可为空=无附加费；空表与 null 同口径） */
    @Valid
    private List<SurchargeItem> surcharges;
    /** 报价有效期至（必填；过期后不能被锁价、不能被新批次派车引用） */
    @NotNull(message = "报价有效期不能为空")
    private LocalDateTime validUntil;
    private String quoteRemark;

    public Long getQuoteBatchId() { return quoteBatchId; }
    public void setQuoteBatchId(Long quoteBatchId) { this.quoteBatchId = quoteBatchId; }
    public Long getCarrierId() { return carrierId; }
    public void setCarrierId(Long carrierId) { this.carrierId = carrierId; }
    public BigDecimal getBaseFreight() { return baseFreight; }
    public void setBaseFreight(BigDecimal baseFreight) { this.baseFreight = baseFreight; }
    public BigDecimal getTaxRate() { return taxRate; }
    public void setTaxRate(BigDecimal taxRate) { this.taxRate = taxRate; }
    public List<SurchargeItem> getSurcharges() { return surcharges; }
    public void setSurcharges(List<SurchargeItem> surcharges) { this.surcharges = surcharges; }
    public LocalDateTime getValidUntil() { return validUntil; }
    public void setValidUntil(LocalDateTime validUntil) { this.validUntil = validUntil; }
    public String getQuoteRemark() { return quoteRemark; }
    public void setQuoteRemark(String quoteRemark) { this.quoteRemark = quoteRemark; }
}

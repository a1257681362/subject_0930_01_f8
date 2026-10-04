package com.evops.coldchain.dto;

import javax.validation.Valid;
import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/**
 * 人工改价申请（V8）：对已锁定锁价单提交改后金额拆分。
 * 只写改后价；锁价单原始报价快照（orig_*）永不修改。必须经审批同意后执行价才生效。
 */
public class PriceAdjustmentApplyRequest {
    @NotNull(message = "锁价单ID不能为空")
    private Long priceLockId;
    @NotNull(message = "改后基础运费不能为空")
    @DecimalMin(value = "0", message = "基础运费不能为负")
    private BigDecimal baseFreight;
    @NotNull(message = "改后税率不能为空")
    @DecimalMin(value = "0", message = "税率不能为负")
    @DecimalMax(value = "0.9999", message = "税率必须小于 1")
    private BigDecimal taxRate;
    @Valid
    private List<SurchargeItem> surcharges;
    @NotBlank(message = "改价事由不能为空")
    private String changeReason;

    public Long getPriceLockId() { return priceLockId; }
    public void setPriceLockId(Long priceLockId) { this.priceLockId = priceLockId; }
    public BigDecimal getBaseFreight() { return baseFreight; }
    public void setBaseFreight(BigDecimal baseFreight) { this.baseFreight = baseFreight; }
    public BigDecimal getTaxRate() { return taxRate; }
    public void setTaxRate(BigDecimal taxRate) { this.taxRate = taxRate; }
    public List<SurchargeItem> getSurcharges() { return surcharges; }
    public void setSurcharges(List<SurchargeItem> surcharges) { this.surcharges = surcharges; }
    public String getChangeReason() { return changeReason; }
    public void setChangeReason(String changeReason) { this.changeReason = changeReason; }
}

package com.evops.coldchain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.CreateOnlyEntity;

import java.math.BigDecimal;

/**
 * 人工改价附加费明细（V8，只追加）：随改价申请提交，审批同意后据此重算执行价附加费合计。
 */
@TableName("t_cc_price_adjustment_surcharge")
public class PriceAdjustmentSurcharge extends CreateOnlyEntity {
    private Long adjustmentId;
    private String feeCode;
    private String feeName;
    private BigDecimal amount;
    private String feeNote;

    public Long getAdjustmentId() { return adjustmentId; }
    public void setAdjustmentId(Long adjustmentId) { this.adjustmentId = adjustmentId; }
    public String getFeeCode() { return feeCode; }
    public void setFeeCode(String feeCode) { this.feeCode = feeCode; }
    public String getFeeName() { return feeName; }
    public void setFeeName(String feeName) { this.feeName = feeName; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getFeeNote() { return feeNote; }
    public void setFeeNote(String feeNote) { this.feeNote = feeNote; }
}

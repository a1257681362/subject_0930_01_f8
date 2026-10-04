package com.evops.coldchain.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 附加费明细项（V8）。报价提交与人工改价申请共用：
 * 费目代码/名称 + 非负金额 + 说明。附加费合计由服务层汇总，客户端不传总价。
 */
public class SurchargeItem {
    @NotBlank(message = "附加费费目代码不能为空")
    private String feeCode;
    @NotBlank(message = "附加费费目名称不能为空")
    private String feeName;
    @NotNull(message = "附加费金额不能为空")
    private BigDecimal amount;
    private String feeNote;

    public String getFeeCode() { return feeCode; }
    public void setFeeCode(String feeCode) { this.feeCode = feeCode; }
    public String getFeeName() { return feeName; }
    public void setFeeName(String feeName) { this.feeName = feeName; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getFeeNote() { return feeNote; }
    public void setFeeNote(String feeNote) { this.feeNote = feeNote; }
}

package com.evops.coldchain.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/** 承运商撤报请求（V8）：撤报只置 WITHDRAWN 保留原行，可在同批次重新报价产生新行 */
public class QuoteWithdrawRequest {
    @NotNull(message = "报价ID不能为空")
    private Long quoteId;
    @NotBlank(message = "撤报原因不能为空")
    private String reason;

    public Long getQuoteId() { return quoteId; }
    public void setQuoteId(Long quoteId) { this.quoteId = quoteId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}

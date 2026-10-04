package com.evops.coldchain.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 报价异常登记请求（V8，只追加）：按询价批次（可定位到具体报价与承运商）登记
 * 超时未报/异常撤报/计价争议/资质不符等异常原因。
 */
public class QuoteExceptionMarkRequest {
    @NotNull(message = "询价批次ID不能为空")
    private Long quoteBatchId;
    /** 关联具体报价；批次级异常（如整体流标）可空 */
    private Long quoteId;
    @NotBlank(message = "异常类型不能为空")
    private String exceptionType;
    @NotBlank(message = "异常原因/描述不能为空")
    private String description;

    public Long getQuoteBatchId() { return quoteBatchId; }
    public void setQuoteBatchId(Long quoteBatchId) { this.quoteBatchId = quoteBatchId; }
    public Long getQuoteId() { return quoteId; }
    public void setQuoteId(Long quoteId) { this.quoteId = quoteId; }
    public String getExceptionType() { return exceptionType; }
    public void setExceptionType(String exceptionType) { this.exceptionType = exceptionType; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}

package com.evops.coldchain.dto;

import javax.validation.constraints.NotNull;

/**
 * 锁价请求（V8）：在询价批次的多条有效报价中选择唯一一个中选方案。
 * 锁价时即时判过期：valid_until 已过的报价不得中选；中选即把报价全字段快照到锁价单。
 */
public class PriceLockRequest {
    @NotNull(message = "询价批次ID不能为空")
    private Long quoteBatchId;
    @NotNull(message = "中选报价ID不能为空")
    private Long wonQuoteId;
    private String remark;

    public Long getQuoteBatchId() { return quoteBatchId; }
    public void setQuoteBatchId(Long quoteBatchId) { this.quoteBatchId = quoteBatchId; }
    public Long getWonQuoteId() { return wonQuoteId; }
    public void setWonQuoteId(Long wonQuoteId) { this.wonQuoteId = wonQuoteId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}

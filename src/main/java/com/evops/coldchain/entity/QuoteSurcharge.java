package com.evops.coldchain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.CreateOnlyEntity;

import java.math.BigDecimal;

/**
 * 报价附加费明细（V8，只追加）：每条报价多笔附加费
 * （温控燃油附加/装卸费/保险费/等候费等），随报价插入，不提供修改删除路径。
 */
@TableName("t_cc_quote_surcharge")
public class QuoteSurcharge extends CreateOnlyEntity {
    private Long quoteId;
    private String feeCode;
    private String feeName;
    private BigDecimal amount;
    private String feeNote;

    public Long getQuoteId() { return quoteId; }
    public void setQuoteId(Long quoteId) { this.quoteId = quoteId; }
    public String getFeeCode() { return feeCode; }
    public void setFeeCode(String feeCode) { this.feeCode = feeCode; }
    public String getFeeName() { return feeName; }
    public void setFeeName(String feeName) { this.feeName = feeName; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getFeeNote() { return feeNote; }
    public void setFeeNote(String feeNote) { this.feeNote = feeNote; }
}

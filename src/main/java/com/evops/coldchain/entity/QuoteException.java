package com.evops.coldchain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDateTime;

/**
 * 报价异常（V8，只追加）：报价超时未报/异常撤报/计价争议/资质不符等按询价批次或具体报价登记。
 * 闭环只追加处理说明、处理人与处理时间，不改原描述；支持从锁价单反查处理人及异常原因。
 */
@TableName("t_cc_quote_exception")
public class QuoteException extends BaseEntity {
    private Long quoteBatchId;
    private Long quoteId;
    private Long carrierId;
    private String carrierName;
    private String exceptionType;
    private String description;
    /** OPEN / RESOLVED */
    private String status;
    private String resolveNote;
    private String resolveName;
    private LocalDateTime resolveTime;

    public Long getQuoteBatchId() { return quoteBatchId; }
    public void setQuoteBatchId(Long quoteBatchId) { this.quoteBatchId = quoteBatchId; }
    public Long getQuoteId() { return quoteId; }
    public void setQuoteId(Long quoteId) { this.quoteId = quoteId; }
    public Long getCarrierId() { return carrierId; }
    public void setCarrierId(Long carrierId) { this.carrierId = carrierId; }
    public String getCarrierName() { return carrierName; }
    public void setCarrierName(String carrierName) { this.carrierName = carrierName; }
    public String getExceptionType() { return exceptionType; }
    public void setExceptionType(String exceptionType) { this.exceptionType = exceptionType; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getResolveNote() { return resolveNote; }
    public void setResolveNote(String resolveNote) { this.resolveNote = resolveNote; }
    public String getResolveName() { return resolveName; }
    public void setResolveName(String resolveName) { this.resolveName = resolveName; }
    public LocalDateTime getResolveTime() { return resolveTime; }
    public void setResolveTime(LocalDateTime resolveTime) { this.resolveTime = resolveTime; }
}

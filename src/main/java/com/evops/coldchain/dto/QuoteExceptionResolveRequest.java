package com.evops.coldchain.dto;

import javax.validation.constraints.NotBlank;

/**
 * 报价异常闭环请求（V8）：id 由控制器按路径 {id} 回填，故体上不做非空校验；
 * 处理说明必填。只追加处理说明与处理人，不改原异常描述。
 */
public class QuoteExceptionResolveRequest {
    /** 报价异常ID（控制器按路径 {id} 回填） */
    private Long id;
    @NotBlank(message = "处理说明不能为空")
    private String resolveNote;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getResolveNote() { return resolveNote; }
    public void setResolveNote(String resolveNote) { this.resolveNote = resolveNote; }
}

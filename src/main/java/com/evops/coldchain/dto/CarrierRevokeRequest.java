package com.evops.coldchain.dto;

import javax.validation.constraints.NotBlank;

/** 承运商撤回报废请求（V8） */
public class CarrierRevokeRequest {
    @NotBlank(message = "撤回原因不能为空")
    private String reason;

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}

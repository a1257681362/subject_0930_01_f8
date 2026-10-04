package com.evops.coldchain.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * 询价批次创建请求（V8）：固化四个报价维度——路线（可空）× 温区 × 箱型 × 服务等级。
 */
public class QuoteBatchCreateRequest {
    /** 路线ID，可空（门到门/未建档路线），非空时必须为在效路线 */
    private Long routeId;
    @NotBlank(message = "温区不能为空")
    private String zoneCode;
    @NotNull(message = "箱型不能为空")
    private Long boxTypeId;
    @NotBlank(message = "服务等级不能为空")
    private String serviceLevel;
    private LocalDateTime expectShipTime;
    private LocalDateTime deadline;
    private String cargoDesc;
    private String remark;

    public Long getRouteId() { return routeId; }
    public void setRouteId(Long routeId) { this.routeId = routeId; }
    public String getZoneCode() { return zoneCode; }
    public void setZoneCode(String zoneCode) { this.zoneCode = zoneCode; }
    public Long getBoxTypeId() { return boxTypeId; }
    public void setBoxTypeId(Long boxTypeId) { this.boxTypeId = boxTypeId; }
    public String getServiceLevel() { return serviceLevel; }
    public void setServiceLevel(String serviceLevel) { this.serviceLevel = serviceLevel; }
    public LocalDateTime getExpectShipTime() { return expectShipTime; }
    public void setExpectShipTime(LocalDateTime expectShipTime) { this.expectShipTime = expectShipTime; }
    public LocalDateTime getDeadline() { return deadline; }
    public void setDeadline(LocalDateTime deadline) { this.deadline = deadline; }
    public String getCargoDesc() { return cargoDesc; }
    public void setCargoDesc(String cargoDesc) { this.cargoDesc = cargoDesc; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}

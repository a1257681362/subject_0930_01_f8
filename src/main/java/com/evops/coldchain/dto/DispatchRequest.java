package com.evops.coldchain.dto;

import javax.validation.constraints.NotNull;

/** 派车请求：发货单 + 承运车辆，生成运输批次并固化档案快照 */
public class DispatchRequest {
    @NotNull(message = "发货单ID不能为空")
    private Long orderId;
    @NotNull(message = "承运车辆ID不能为空")
    private Long vehicleId;
    /**
     * 可选运输路线ID：传入则按派车时点的季节选取该路线生效版本，逐段固化温区分段快照。
     * 不传则保持 V1 全程单一档案温区口径。
     */
    private Long routeId;
    /**
     * V7 可选监管区域：传入则随批次固化，作为抽检查询区域隔离依据；
     * 不传也可在首次对该批次立项抽检时补登，补登后不再改变。
     */
    private String superviseRegion;
    /**
     * V8 可选锁价单ID：传入则派车消费该锁价单（必须处于 LOCKED；改价审批中/已作废/已被别的
     * 批次消费均拒绝），把承运商与执行价快照到批次。不传则按无锁价派车（V1~V7 口径不变）。
     */
    private Long priceLockId;

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getVehicleId() { return vehicleId; }
    public void setVehicleId(Long vehicleId) { this.vehicleId = vehicleId; }
    public Long getRouteId() { return routeId; }
    public void setRouteId(Long routeId) { this.routeId = routeId; }
    public String getSuperviseRegion() { return superviseRegion; }
    public void setSuperviseRegion(String superviseRegion) { this.superviseRegion = superviseRegion; }
    public Long getPriceLockId() { return priceLockId; }
    public void setPriceLockId(Long priceLockId) { this.priceLockId = priceLockId; }
}

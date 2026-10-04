package com.evops.coldchain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDateTime;

/**
 * 询价批次（V8）：一次承运商报价比选的四个报价维度（路线 × 温区 × 箱型 × 服务等级）
 * 在创建时固化，多个承运商对同一批次并行报价。
 * 状态：OPEN 比选中 / CLOSED 已锁价结束 / EXPIRED 全部报价过期未锁 / VOIDED 作废。
 */
@TableName("t_cc_quote_batch")
public class QuoteBatch extends BaseEntity {
    private String quoteBatchNo;
    private Long routeId;
    private String routeNameSnap;
    private String zoneCode;
    private String zoneName;
    private Long boxTypeId;
    private String boxTypeCodeSnap;
    private String boxTypeNameSnap;
    private String serviceLevel;
    private String serviceLevelName;
    private LocalDateTime expectShipTime;
    private String cargoDesc;
    private String status;
    private LocalDateTime deadline;
    private LocalDateTime closeTime;
    private String remark;

    public String getQuoteBatchNo() { return quoteBatchNo; }
    public void setQuoteBatchNo(String quoteBatchNo) { this.quoteBatchNo = quoteBatchNo; }
    public Long getRouteId() { return routeId; }
    public void setRouteId(Long routeId) { this.routeId = routeId; }
    public String getRouteNameSnap() { return routeNameSnap; }
    public void setRouteNameSnap(String routeNameSnap) { this.routeNameSnap = routeNameSnap; }
    public String getZoneCode() { return zoneCode; }
    public void setZoneCode(String zoneCode) { this.zoneCode = zoneCode; }
    public String getZoneName() { return zoneName; }
    public void setZoneName(String zoneName) { this.zoneName = zoneName; }
    public Long getBoxTypeId() { return boxTypeId; }
    public void setBoxTypeId(Long boxTypeId) { this.boxTypeId = boxTypeId; }
    public String getBoxTypeCodeSnap() { return boxTypeCodeSnap; }
    public void setBoxTypeCodeSnap(String boxTypeCodeSnap) { this.boxTypeCodeSnap = boxTypeCodeSnap; }
    public String getBoxTypeNameSnap() { return boxTypeNameSnap; }
    public void setBoxTypeNameSnap(String boxTypeNameSnap) { this.boxTypeNameSnap = boxTypeNameSnap; }
    public String getServiceLevel() { return serviceLevel; }
    public void setServiceLevel(String serviceLevel) { this.serviceLevel = serviceLevel; }
    public String getServiceLevelName() { return serviceLevelName; }
    public void setServiceLevelName(String serviceLevelName) { this.serviceLevelName = serviceLevelName; }
    public LocalDateTime getExpectShipTime() { return expectShipTime; }
    public void setExpectShipTime(LocalDateTime expectShipTime) { this.expectShipTime = expectShipTime; }
    public String getCargoDesc() { return cargoDesc; }
    public void setCargoDesc(String cargoDesc) { this.cargoDesc = cargoDesc; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getDeadline() { return deadline; }
    public void setDeadline(LocalDateTime deadline) { this.deadline = deadline; }
    public LocalDateTime getCloseTime() { return closeTime; }
    public void setCloseTime(LocalDateTime closeTime) { this.closeTime = closeTime; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}

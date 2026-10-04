package com.evops.coldchain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

/**
 * 冷链承运商主档（V8）。报价比选以稳定承运商为主体（区别于车辆登记中的自由文本 carrierName）。
 * ACTIVE / REVOKED：撤回只置状态，不删除，历史报价与锁价快照仍可追溯。
 */
@TableName("t_cc_carrier")
public class Carrier extends BaseEntity {
    private String carrierNo;
    private String carrierName;
    private String contactName;
    private String contactPhone;
    private String qualification;
    private String status;
    private String remark;

    public String getCarrierNo() { return carrierNo; }
    public void setCarrierNo(String carrierNo) { this.carrierNo = carrierNo; }
    public String getCarrierName() { return carrierName; }
    public void setCarrierName(String carrierName) { this.carrierName = carrierName; }
    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }
    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }
    public String getQualification() { return qualification; }
    public void setQualification(String qualification) { this.qualification = qualification; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}

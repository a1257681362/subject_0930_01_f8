package com.evops.coldchain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;

/**
 * 保温箱箱型主档（V8）。报价维度之一（路线 × 温区 × 箱型 × 服务等级）。
 */
@TableName("t_cc_box_type")
public class BoxType extends BaseEntity {
    private String boxTypeNo;
    private String boxTypeCode;
    private String boxTypeName;
    private BigDecimal volumeLiter;
    private String status;
    private String remark;

    public String getBoxTypeNo() { return boxTypeNo; }
    public void setBoxTypeNo(String boxTypeNo) { this.boxTypeNo = boxTypeNo; }
    public String getBoxTypeCode() { return boxTypeCode; }
    public void setBoxTypeCode(String boxTypeCode) { this.boxTypeCode = boxTypeCode; }
    public String getBoxTypeName() { return boxTypeName; }
    public void setBoxTypeName(String boxTypeName) { this.boxTypeName = boxTypeName; }
    public BigDecimal getVolumeLiter() { return volumeLiter; }
    public void setVolumeLiter(BigDecimal volumeLiter) { this.volumeLiter = volumeLiter; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}

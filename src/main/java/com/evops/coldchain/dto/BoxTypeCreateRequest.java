package com.evops.coldchain.dto;

import javax.validation.constraints.NotBlank;
import java.math.BigDecimal;

/** 箱型建档请求（V8） */
public class BoxTypeCreateRequest {
    @NotBlank(message = "箱型代码不能为空")
    private String boxTypeCode;
    @NotBlank(message = "箱型名称不能为空")
    private String boxTypeName;
    private BigDecimal volumeLiter;
    private String remark;

    public String getBoxTypeCode() { return boxTypeCode; }
    public void setBoxTypeCode(String boxTypeCode) { this.boxTypeCode = boxTypeCode; }
    public String getBoxTypeName() { return boxTypeName; }
    public void setBoxTypeName(String boxTypeName) { this.boxTypeName = boxTypeName; }
    public BigDecimal getVolumeLiter() { return volumeLiter; }
    public void setVolumeLiter(BigDecimal volumeLiter) { this.volumeLiter = volumeLiter; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}

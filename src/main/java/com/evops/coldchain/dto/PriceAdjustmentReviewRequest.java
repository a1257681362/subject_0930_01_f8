package com.evops.coldchain.dto;

import javax.validation.constraints.NotBlank;

/** 人工改价审批（同意/驳回，必须留意见）。复用 V7 审批意见口径。 */
public class PriceAdjustmentReviewRequest {
    @NotBlank(message = "审批意见不能为空")
    private String reviewNote;

    public String getReviewNote() { return reviewNote; }
    public void setReviewNote(String reviewNote) { this.reviewNote = reviewNote; }
}

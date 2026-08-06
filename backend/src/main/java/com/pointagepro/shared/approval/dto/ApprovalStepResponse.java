package com.pointagepro.shared.approval.dto;

import com.pointagepro.shared.approval.entity.Approval;

import java.time.LocalDateTime;

/**
 * Response DTO for one step of an approval chain (attendance adjustments and leaves).
 */
public class ApprovalStepResponse {

    private Long id;
    private Integer stepOrder;
    private String approverRole;
    private String statusCode;
    private Long approverId;
    private String approverName;
    private String comment;
    private LocalDateTime decidedAt;

    public ApprovalStepResponse() {
    }

    public static ApprovalStepResponse from(Approval step) {
        ApprovalStepResponse r = new ApprovalStepResponse();
        r.id = step.getId();
        r.stepOrder = step.getStepOrder();
        r.approverRole = step.getApproverRole();
        r.statusCode = step.getStatus().getCode();
        if (step.getApprover() != null) {
            r.approverId = step.getApprover().getId();
            r.approverName = step.getApprover().getFullName();
        }
        r.comment = step.getComment();
        r.decidedAt = step.getDecidedAt();
        return r;
    }

    public Long getId() { return id; }
    public Integer getStepOrder() { return stepOrder; }
    public String getApproverRole() { return approverRole; }
    public String getStatusCode() { return statusCode; }
    public Long getApproverId() { return approverId; }
    public String getApproverName() { return approverName; }
    public String getComment() { return comment; }
    public LocalDateTime getDecidedAt() { return decidedAt; }
}

package com.pointagepro.attendance.dto;

import com.pointagepro.attendance.entity.AttendanceAdjustment;
import com.pointagepro.shared.approval.dto.ApprovalStepResponse;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for an attendance adjustment, including its materialized approval chain.
 * Built from entities loaded with {@code AttendanceAdjustmentRepository} entity graphs so
 * mapping may safely happen after the transaction commits.
 */
public class AdjustmentResponse {

    private Long id;
    private Long employeeId;
    private String employeeName;
    private LocalDate workDate;
    private String adjustmentTypeCode;
    private String adjustmentTypeLabel;
    private Integer minutes;
    private String reason;
    private String statusCode;
    private String statusLabel;
    private Long summaryId;
    private Approver createdBy;
    private Approver approvedBy;
    private LocalDateTime approvedAt;
    private LocalDateTime createdAt;
    private List<ApprovalStepResponse> approvals;

    public AdjustmentResponse() {
    }

    public static AdjustmentResponse from(AttendanceAdjustment a, List<ApprovalStepResponse> approvals) {
        AdjustmentResponse r = new AdjustmentResponse();
        r.id = a.getId();
        r.employeeId = a.getEmployee().getId();
        r.employeeName = a.getEmployee().getFirstName() + " " + a.getEmployee().getLastName();
        r.workDate = a.getWorkDate();
        r.adjustmentTypeCode = a.getAdjustmentType().getCode();
        r.adjustmentTypeLabel = a.getAdjustmentType().getLabel();
        r.minutes = a.getMinutes();
        r.reason = a.getReason();
        r.statusCode = a.getStatus().getCode();
        r.statusLabel = a.getStatus().getLabel();
        r.summaryId = a.getSummary() != null ? a.getSummary().getId() : null;
        r.createdBy = a.getCreatedBy() != null ? new Approver(a.getCreatedBy()) : null;
        r.approvedBy = a.getApprovedBy() != null ? new Approver(a.getApprovedBy()) : null;
        r.approvedAt = a.getApprovedAt();
        r.createdAt = a.getCreatedAt();
        r.approvals = approvals;
        return r;
    }

    public Long getId() { return id; }
    public Long getEmployeeId() { return employeeId; }
    public String getEmployeeName() { return employeeName; }
    public LocalDate getWorkDate() { return workDate; }
    public String getAdjustmentTypeCode() { return adjustmentTypeCode; }
    public String getAdjustmentTypeLabel() { return adjustmentTypeLabel; }
    public Integer getMinutes() { return minutes; }
    public String getReason() { return reason; }
    public String getStatusCode() { return statusCode; }
    public String getStatusLabel() { return statusLabel; }
    public Long getSummaryId() { return summaryId; }
    public Approver getCreatedBy() { return createdBy; }
    public Approver getApprovedBy() { return approvedBy; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public List<ApprovalStepResponse> getApprovals() { return approvals; }

    public static class Approver {
        private final Long id;
        private final String fullName;

        public Approver(com.pointagepro.auth.entity.User user) {
            this.id = user.getId();
            this.fullName = user.getFullName();
        }

        public Long getId() { return id; }
        public String getFullName() { return fullName; }
    }
}

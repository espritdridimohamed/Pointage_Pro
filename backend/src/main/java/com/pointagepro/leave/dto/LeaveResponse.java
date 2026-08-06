package com.pointagepro.leave.dto;

import com.pointagepro.auth.entity.User;
import com.pointagepro.leave.entity.LeaveRequest;
import com.pointagepro.shared.approval.dto.ApprovalStepResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for one leave request, including its materialized approval chain.
 * Built from entities loaded with {@code LeaveRequestRepository} entity graphs so
 * mapping may safely happen after the transaction commits.
 */
public class LeaveResponse {

    private Long id;
    private Long employeeId;
    private String employeeName;
    private String leaveTypeCode;
    private String leaveTypeName;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal daysRequested;
    private String reason;
    private String attachmentPath;
    private String statusCode;
    private String statusLabel;
    private Approver createdBy;
    private Approver approvedBy;
    private LocalDateTime approvedAt;
    private String rejectedReason;
    private LocalDateTime createdAt;
    private List<ApprovalStepResponse> approvals;

    public LeaveResponse() {
    }

    public static LeaveResponse from(LeaveRequest r, List<ApprovalStepResponse> approvals) {
        LeaveResponse dto = new LeaveResponse();
        dto.id = r.getId();
        dto.employeeId = r.getEmployee().getId();
        dto.employeeName = r.getEmployee().getFirstName() + " " + r.getEmployee().getLastName();
        dto.leaveTypeCode = r.getLeaveType().getCode();
        dto.leaveTypeName = r.getLeaveType().getName();
        dto.startDate = r.getStartDate();
        dto.endDate = r.getEndDate();
        dto.daysRequested = r.getDaysRequested();
        dto.reason = r.getReason();
        dto.attachmentPath = r.getAttachmentPath();
        dto.statusCode = r.getStatus().getCode();
        dto.statusLabel = r.getStatus().getLabel();
        dto.createdBy = r.getCreatedBy() != null ? new Approver(r.getCreatedBy()) : null;
        dto.approvedBy = r.getApprovedBy() != null ? new Approver(r.getApprovedBy()) : null;
        dto.approvedAt = r.getApprovedAt();
        dto.rejectedReason = r.getRejectedReason();
        dto.createdAt = r.getCreatedAt();
        dto.approvals = approvals;
        return dto;
    }

    public Long getId() { return id; }
    public Long getEmployeeId() { return employeeId; }
    public String getEmployeeName() { return employeeName; }
    public String getLeaveTypeCode() { return leaveTypeCode; }
    public String getLeaveTypeName() { return leaveTypeName; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public BigDecimal getDaysRequested() { return daysRequested; }
    public String getReason() { return reason; }
    public String getAttachmentPath() { return attachmentPath; }
    public String getStatusCode() { return statusCode; }
    public String getStatusLabel() { return statusLabel; }
    public Approver getCreatedBy() { return createdBy; }
    public Approver getApprovedBy() { return approvedBy; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public String getRejectedReason() { return rejectedReason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public List<ApprovalStepResponse> getApprovals() { return approvals; }

    public static class Approver {
        private final Long id;
        private final String fullName;

        public Approver(User user) {
            this.id = user.getId();
            this.fullName = user.getFullName();
        }

        public Long getId() { return id; }
        public String getFullName() { return fullName; }
    }
}

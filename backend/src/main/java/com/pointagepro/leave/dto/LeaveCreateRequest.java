package com.pointagepro.leave.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Body of {@code POST /api/v1/leaves} — a new leave request. The employee, type,
 * date span and lengths are validated in {@code LeaveService} (tenant, scope, active
 * type, start &lt;= end, 366-day span, overlap guard). {@code daysRequested} is
 * server-computed and never accepted from the client.
 */
public class LeaveCreateRequest {

    @NotNull(message = "employeeId is required")
    private Long employeeId;

    @NotBlank(message = "leaveTypeCode is required")
    private String leaveTypeCode;

    @NotNull(message = "startDate is required")
    private LocalDate startDate;

    @NotNull(message = "endDate is required")
    private LocalDate endDate;

    @Size(max = 500, message = "reason must be at most 500 characters")
    private String reason;

    @Size(max = 255, message = "attachmentPath must be at most 255 characters")
    private String attachmentPath;

    public LeaveCreateRequest() {
    }

    public LeaveCreateRequest(Long employeeId, String leaveTypeCode, LocalDate startDate,
                              LocalDate endDate, String reason, String attachmentPath) {
        this.employeeId = employeeId;
        this.leaveTypeCode = leaveTypeCode;
        this.startDate = startDate;
        this.endDate = endDate;
        this.reason = reason;
        this.attachmentPath = attachmentPath;
    }

    public Long getEmployeeId() { return employeeId; }
    public String getLeaveTypeCode() { return leaveTypeCode; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public String getReason() { return reason; }
    public String getAttachmentPath() { return attachmentPath; }
}

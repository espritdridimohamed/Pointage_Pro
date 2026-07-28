package com.pointagepro.leave.dto;

import com.pointagepro.leave.LeaveRequest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public class LeaveRequestResponse {

    private Long id;
    private Long employeeId;
    private String firstName;
    private String lastName;
    private String initials;
    private String avatarColor;
    private String leaveType;
    private LocalDate startDate;
    private LocalDate endDate;
    private long days;
    private String reason;
    private boolean hasAttachment;
    private String attachment;
    private String photo;
    private String status;
    private String requestedDate;
    private String approvedByName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private static final String[] AVATAR_COLORS = {
        "#2563EB", "#10B981", "#EC4899", "#F59E0B", "#8B5CF6", "#06B6D4"
    };

    public static LeaveRequestResponse fromLeaveRequest(LeaveRequest lr) {
        LeaveRequestResponse response = new LeaveRequestResponse();
        mapCommon(response, lr);
        return response;
    }

    public static LeaveRequestResponse fromLeaveRequestDetail(LeaveRequest lr) {
        LeaveRequestResponse response = new LeaveRequestResponse();
        mapCommon(response, lr);
        response.attachment = lr.getAttachment();
        return response;
    }

    private static void mapCommon(LeaveRequestResponse response, LeaveRequest lr) {
        response.id = lr.getId();
        response.employeeId = lr.getEmployee().getId();
        response.firstName = lr.getEmployee().getFirstName();
        response.lastName = lr.getEmployee().getLastName();
        response.initials = (lr.getEmployee().getFirstName().substring(0, 1) +
                lr.getEmployee().getLastName().substring(0, 1)).toUpperCase();
        long colorIndex = Math.abs(lr.getEmployee().getId()) % AVATAR_COLORS.length;
        response.avatarColor = AVATAR_COLORS[(int) colorIndex];
        response.photo = lr.getEmployee().getPhoto();
        response.leaveType = lr.getLeaveType();
        response.startDate = lr.getStartDate();
        response.endDate = lr.getEndDate();
        response.days = ChronoUnit.DAYS.between(lr.getStartDate(), lr.getEndDate()) + 1;
        response.reason = lr.getReason();
        response.hasAttachment = lr.getAttachment() != null && !lr.getAttachment().isBlank();
        response.status = mapStatus(lr.getStatus());
        response.requestedDate = lr.getCreatedAt() != null ?
                lr.getCreatedAt().toLocalDate().toString() : null;
        response.approvedByName = lr.getApprovedBy() != null ?
                lr.getApprovedBy().getFullName() : null;
        response.createdAt = lr.getCreatedAt();
        response.updatedAt = lr.getUpdatedAt();
    }

    private static String mapStatus(String dbStatus) {
        return switch (dbStatus) {
            case "APPROVED" -> "Approuvé";
            case "REFUSED" -> "Refusé";
            default -> "En cours";
        };
    }

    public Long getId() { return id; }
    public Long getEmployeeId() { return employeeId; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getInitials() { return initials; }
    public String getAvatarColor() { return avatarColor; }
    public String getPhoto() { return photo; }
    public String getLeaveType() { return leaveType; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public long getDays() { return days; }
    public String getReason() { return reason; }
    public boolean isHasAttachment() { return hasAttachment; }
    public String getAttachment() { return attachment; }
    public String getStatus() { return status; }
    public String getRequestedDate() { return requestedDate; }
    public String getApprovedByName() { return approvedByName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}

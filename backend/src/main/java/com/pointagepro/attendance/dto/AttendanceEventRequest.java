package com.pointagepro.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public class AttendanceEventRequest {

    @NotNull(message = "employeeId is required")
    private Long employeeId;

    @NotBlank(message = "eventType is required")
    @Pattern(regexp = "IN|OUT", message = "eventType must be IN or OUT")
    private String eventType;

    @NotNull(message = "timestamp is required")
    private LocalDateTime timestamp;

    @Size(max = 20, message = "source must be at most 20 characters")
    private String source;

    public AttendanceEventRequest() {
    }

    public AttendanceEventRequest(Long employeeId, String eventType, LocalDateTime timestamp, String source) {
        this.employeeId = employeeId;
        this.eventType = eventType;
        this.timestamp = timestamp;
        this.source = source;
    }

    public Long getEmployeeId() { return employeeId; }
    public String getEventType() { return eventType; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public String getSource() { return source; }
}

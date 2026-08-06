package com.pointagepro.attendance.dto;

import com.pointagepro.attendance.entity.AttendanceEvent;
import com.pointagepro.auth.entity.User;

import java.time.LocalDateTime;

public class AttendanceEventResponse {

    private Long id;
    private Long employeeId;
    private String employeeName;
    private String eventType;
    private LocalDateTime timestamp;
    private String source;
    private Long terminalId;
    private String terminalCode;
    private String externalRef;
    private boolean timeWarning;
    private ComputedBy computedBy;

    public AttendanceEventResponse() {
    }

    public static AttendanceEventResponse from(AttendanceEvent event) {
        AttendanceEventResponse r = new AttendanceEventResponse();
        r.id = event.getId();
        r.employeeId = event.getEmployee().getId();
        r.employeeName = event.getEmployee().getFirstName() + " " + event.getEmployee().getLastName();
        r.eventType = event.getEventType().getCode();
        r.timestamp = event.getEventTime();
        r.source = event.getSource();
        r.terminalId = event.getTerminalId();
        r.externalRef = event.getExternalRef();
        r.timeWarning = Boolean.TRUE.equals(event.getTimeWarning());
        return r;
    }

    public Long getId() { return id; }
    public Long getEmployeeId() { return employeeId; }
    public String getEmployeeName() { return employeeName; }
    public String getEventType() { return eventType; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public String getSource() { return source; }
    public Long getTerminalId() { return terminalId; }
    public String getTerminalCode() { return terminalCode; }
    public String getExternalRef() { return externalRef; }
    public boolean isTimeWarning() { return timeWarning; }
    public ComputedBy getComputedBy() { return computedBy; }

    public void setTerminalCode(String terminalCode) { this.terminalCode = terminalCode; }
    public void setComputedBy(User user) {
        this.computedBy = user == null ? null : new ComputedBy(user.getId(), user.getFullName());
    }

    public static class ComputedBy {
        private final Long id;
        private final String fullName;

        public ComputedBy(Long id, String fullName) {
            this.id = id;
            this.fullName = fullName;
        }

        public Long getId() { return id; }
        public String getFullName() { return fullName; }
    }
}

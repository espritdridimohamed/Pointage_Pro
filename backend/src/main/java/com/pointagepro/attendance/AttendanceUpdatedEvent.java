package com.pointagepro.attendance;

import org.springframework.context.ApplicationEvent;

public class AttendanceUpdatedEvent extends ApplicationEvent {

    private final Long employeeId;
    private final int month;
    private final int year;

    public AttendanceUpdatedEvent(Object source, Long employeeId, int month, int year) {
        super(source);
        this.employeeId = employeeId;
        this.month = month;
        this.year = year;
    }

    public Long getEmployeeId() { return employeeId; }
    public int getMonth() { return month; }
    public int getYear() { return year; }
}

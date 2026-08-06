package com.pointagepro.attendance.dto;

import com.pointagepro.attendance.entity.AttendanceEvent;
import com.pointagepro.employee.entity.Employee;

import java.time.format.DateTimeFormatter;

/**
 * Service → controller outcome for event intake. Genuine rejections (UNKNOWN_BADGE,
 * TERMINAL_INACTIVE) are returned as values, never thrown, so the device controller can
 * answer a flat success:false body; the staff controller maps them to HTTP errors.
 *
 * {@code action} (IN/OUT) and {@code time} (HH:mm) are computed inside the service
 * transaction where the entity associations are initialized, so controllers never touch
 * lazy proxies on detached entities.
 */
public record AttendanceEventResult(Status status, AttendanceEvent event, Employee employee,
                                    String action, String time) {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    public enum Status {
        STORED,
        DUPLICATE,
        REPLAY,
        UNKNOWN_BADGE,
        INVALID_TYPE
    }

    public static AttendanceEventResult stored(AttendanceEvent event, Employee employee) {
        return new AttendanceEventResult(Status.STORED, event, employee,
                event.getEventType().getCode(),
                event.getEventTime().format(TIME));
    }

    public static AttendanceEventResult duplicate() {
        return new AttendanceEventResult(Status.DUPLICATE, null, null, null, null);
    }

    public static AttendanceEventResult replay() {
        return new AttendanceEventResult(Status.REPLAY, null, null, null, null);
    }

    public static AttendanceEventResult unknownBadge() {
        return new AttendanceEventResult(Status.UNKNOWN_BADGE, null, null, null, null);
    }

    public static AttendanceEventResult invalidType() {
        return new AttendanceEventResult(Status.INVALID_TYPE, null, null, null, null);
    }
}

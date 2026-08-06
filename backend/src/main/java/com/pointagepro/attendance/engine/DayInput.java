package com.pointagepro.attendance.engine;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Pure input for one employee/day computation. All resolution (schedule,
 * holiday, leave coverage, event gathering) is done by the service layer;
 * this record carries the already-resolved facts.
 */
public record DayInput(
        long employeeId,
        LocalDate workDate,
        DayKind kind,
        boolean onApprovedLeave,
        LocalTime startTime,
        LocalTime endTime,
        int breakMinutes,
        List<EventInput> events,
        List<AdjustmentInput> adjustments) {
}

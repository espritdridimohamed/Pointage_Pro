package com.pointagepro.attendance.engine;

import java.time.LocalTime;
import java.util.List;

/**
 * Pure attendance engine: turns one day's resolved facts into a DayResult.
 * Follows ATTENDANCE_BUSINESS_RULES.md + ATTENDANCE_ENGINE_TEST_MATRIX.md
 * (status resolution order, first-IN/last-OUT, HALF_DAY = incomplete evidence,
 * missing_minutes = absence only, day-class status for worked weekends/holidays).
 * No I/O, no state -> trivially unit-testable and idempotent.
 */
public final class DayCalculator {

    public static final int MAX_DAILY_WORKED_MINUTES = 1440;

    private DayCalculator() {
    }

    public static DayResult calculate(DayInput in) {
        if (in.onApprovedLeave()) {
            return leaveResult();
        }

        DayResult r = new DayResult();
        int start = toMinute(in.startTime());
        int end = toMinute(in.endTime());
        boolean hasBounds = in.kind() == DayKind.WORKDAY && start >= 0 && end >= 0;

        // night shift: end_time < start_time -> treat end as +24h
        boolean nightShift = hasBounds && end < start;
        if (nightShift) {
            end += 1440;
        }
        int effectiveDay = hasBounds ? Math.max(0, (end - start) - Math.max(0, in.breakMinutes())) : -1;

        Integer firstIn = null;
        Integer lastOut = null;
        for (EventInput e : in.events()) {
            if ("IN".equals(e.typeCode())) {
                if (firstIn == null || e.minuteOfDay() < firstIn) firstIn = e.minuteOfDay();
            } else if ("OUT".equals(e.typeCode())) {
                if (lastOut == null || e.minuteOfDay() > lastOut) lastOut = e.minuteOfDay();
            }
        }

        // night shift: an OUT before the shift start belongs to the next calendar day
        Integer lastOutEff = lastOut;
        if (nightShift && lastOut != null && lastOut < start) {
            lastOutEff = lastOut + 1440;
        }

        // Weekend / holiday: day class wins; all worked minutes are OT, no break subtraction.
        if (in.kind() == DayKind.WEEKEND || in.kind() == DayKind.HOLIDAY) {
            r.setStatusCode(in.kind() == DayKind.WEEKEND ? "WEEKEND" : "HOLIDAY");
            if (firstIn != null && lastOut != null) {
                int worked = Math.max(0, lastOut - firstIn);
                r.setWorkedMinutes(worked);
                r.setOvertimeMinutes(worked);
                r.setFirstInMinute(firstIn);
                r.setLastOutMinute(lastOut);
            }
            applyAdjustments(r, in.adjustments(), effectiveDay);
            return r;
        }

        if (in.kind() == DayKind.NOT_SCHEDULED) {
            r.setStatusCode("NOT_SCHEDULED");
            if (firstIn != null && lastOut != null) {
                r.setWorkedMinutes(Math.max(0, lastOut - firstIn));
            }
            applyAdjustments(r, in.adjustments(), effectiveDay);
            return r;
        }

        // WORKDAY with schedule bounds
        if (firstIn == null && lastOut == null) {
            r.setStatusCode("ABSENT");
            r.setMissingMinutes(effectiveDay);
            applyAdjustments(r, in.adjustments(), effectiveDay);
            return r;
        }

        if (firstIn == null) {
            // OUT only -> check-in assumed at scheduled start (valid OUT proves presence)
            int worked = Math.max(0, (lastOutEff - start) - Math.max(0, in.breakMinutes()));
            int early = Math.max(0, end - lastOutEff);
            r.setStatusCode("HALF_DAY");
            r.setWorkedMinutes(worked);
            r.setLastOutMinute(lastOut);
            r.setEarlyExitMinutes(early);
            r.setMissingMinutes(Math.max(0, effectiveDay - worked));
            applyAdjustments(r, in.adjustments(), effectiveDay);
            return r;
        }

        if (lastOut == null) {
            // IN only -> check-out assumed at scheduled end
            int worked = Math.max(0, (end - firstIn) - Math.max(0, in.breakMinutes()));
            int late = Math.max(0, firstIn - start);
            r.setStatusCode("HALF_DAY");
            r.setWorkedMinutes(worked);
            r.setFirstInMinute(firstIn);
            r.setLateMinutes(late);
            r.setMissingMinutes(Math.max(0, effectiveDay - worked));
            applyAdjustments(r, in.adjustments(), effectiveDay);
            return r;
        }

        // Full pairing: first IN / last OUT
        int worked = Math.max(0, (lastOutEff - firstIn) - Math.max(0, in.breakMinutes()));
        int late = Math.max(0, firstIn - start);
        int early = Math.max(0, end - lastOutEff);
        int ot = Math.max(0, lastOutEff - end);
        r.setStatusCode(late > 0 ? "LATE" : "PRESENT");
        r.setWorkedMinutes(worked);
        r.setFirstInMinute(firstIn);
        r.setLastOutMinute(lastOut);
        r.setLateMinutes(late);
        r.setEarlyExitMinutes(early);
        r.setOvertimeMinutes(ot);
        applyAdjustments(r, in.adjustments(), effectiveDay);
        return r;
    }

    private static void applyAdjustments(DayResult r, List<AdjustmentInput> adjustments, int effectiveDay) {
        int netWorked = 0;
        int netOvertime = 0;
        boolean setAbsent = false;
        for (AdjustmentInput a : adjustments) {
            switch (a.typeCode()) {
                case "ADD_MINUTES" -> netWorked += a.minutes();
                case "REMOVE_MINUTES" -> netWorked -= a.minutes();
                case "ADD_OVERTIME" -> netOvertime += a.minutes();
                case "REMOVE_OVERTIME" -> netOvertime -= a.minutes();
                case "SET_ABSENT" -> setAbsent = true;
                default -> {
                }
            }
        }
        if (setAbsent) {
            r.setWorkedMinutes(0);
            r.setLateMinutes(0);
            r.setEarlyExitMinutes(0);
            r.setOvertimeMinutes(0);
            r.setMissingMinutes(Math.max(0, effectiveDay));
            r.setAdjustmentMinutes(0);
            r.setStatusCode("ABSENT");
            return;
        }
        if (netWorked != 0 || netOvertime != 0) {
            int worked = Math.max(0, r.getWorkedMinutes() + netWorked);
            int ot = Math.max(0, r.getOvertimeMinutes() + netOvertime);
            r.setRawWorkedMinutes(worked);
            r.setWorkedMinutes(Math.min(worked, MAX_DAILY_WORKED_MINUTES));
            r.setOvertimeMinutes(ot);
            r.setAdjustmentMinutes(netWorked + netOvertime);
            r.setStatusCode("ADJUSTED");
        }
    }

    private static DayResult leaveResult() {
        DayResult r = new DayResult();
        r.setStatusCode("LEAVE");
        return r;
    }

    private static int toMinute(LocalTime t) {
        return t == null ? -1 : t.getHour() * 60 + t.getMinute();
    }
}

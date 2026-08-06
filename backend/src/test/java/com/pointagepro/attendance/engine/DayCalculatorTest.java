package com.pointagepro.attendance.engine;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Acceptance tests: every row of ATTENDANCE_ENGINE_TEST_MATRIX.md (16 scenarios).
 * Standard schedule Monday-Friday 08:00-17:00 break 60 (effective day 480 min).
 * Night shift 22:00-06:00 break 0 (effective day 480 min), work date = shift start.
 */
class DayCalculatorTest {

    private static final LocalDate MON = LocalDate.of(2026, 8, 3); // a Monday
    private static final LocalTime START = LocalTime.of(8, 0);
    private static final LocalTime END = LocalTime.of(17, 0);

    private static DayInput workday(List<EventInput> events) {
        return new DayInput(1L, MON, DayKind.WORKDAY, false, START, END, 60, events, List.of());
    }

    private static EventInput in(int hour, int minute) {
        return new EventInput("IN", hour * 60 + minute);
    }

    private static EventInput out(int hour, int minute) {
        return new EventInput("OUT", hour * 60 + minute);
    }

    @Test
    void scenario1_normalInOut() {
        DayResult r = DayCalculator.calculate(workday(List.of(in(8, 0), out(17, 0))));
        assertThat(r.getStatusCode()).isEqualTo("PRESENT");
        assertThat(r.getFirstInMinute()).isEqualTo(480);
        assertThat(r.getLastOutMinute()).isEqualTo(1020);
        assertThat(r.getWorkedMinutes()).isEqualTo(480);
        assertThat(r.getMissingMinutes()).isZero();
        assertThat(r.getLateMinutes()).isZero();
        assertThat(r.getEarlyExitMinutes()).isZero();
        assertThat(r.getOvertimeMinutes()).isZero();
    }

    @Test
    void scenario2_lateArrival() {
        DayResult r = DayCalculator.calculate(workday(List.of(in(8, 30), out(17, 0))));
        assertThat(r.getStatusCode()).isEqualTo("LATE");
        assertThat(r.getWorkedMinutes()).isEqualTo(450);
        assertThat(r.getLateMinutes()).isEqualTo(30);
        assertThat(r.getMissingMinutes()).isZero();
        assertThat(r.getOvertimeMinutes()).isZero();
    }

    @Test
    void scenario3_earlyDeparture() {
        DayResult r = DayCalculator.calculate(workday(List.of(in(8, 0), out(16, 0))));
        assertThat(r.getStatusCode()).isEqualTo("PRESENT");
        assertThat(r.getWorkedMinutes()).isEqualTo(420);
        assertThat(r.getEarlyExitMinutes()).isEqualTo(60);
        assertThat(r.getMissingMinutes()).isZero();
        assertThat(r.getOvertimeMinutes()).isZero();
    }

    @Test
    void scenario4_missingIn() {
        DayResult r = DayCalculator.calculate(workday(List.of(out(17, 0))));
        assertThat(r.getStatusCode()).isEqualTo("HALF_DAY");
        assertThat(r.getWorkedMinutes()).isEqualTo(480);
        assertThat(r.getLastOutMinute()).isEqualTo(1020);
        assertThat(r.getMissingMinutes()).isZero();
        assertThat(r.getLateMinutes()).isZero();
        assertThat(r.getOvertimeMinutes()).isZero();
    }

    @Test
    void scenario5_missingOut() {
        DayResult r = DayCalculator.calculate(workday(List.of(in(8, 0))));
        assertThat(r.getStatusCode()).isEqualTo("HALF_DAY");
        assertThat(r.getWorkedMinutes()).isEqualTo(480);
        assertThat(r.getFirstInMinute()).isEqualTo(480);
        assertThat(r.getMissingMinutes()).isZero();
        assertThat(r.getLateMinutes()).isZero();
        assertThat(r.getOvertimeMinutes()).isZero();
    }

    @Test
    void scenario6_noScanAbsence() {
        DayResult r = DayCalculator.calculate(workday(List.of()));
        assertThat(r.getStatusCode()).isEqualTo("ABSENT");
        assertThat(r.getWorkedMinutes()).isZero();
        assertThat(r.getMissingMinutes()).isEqualTo(480);
        assertThat(r.getFirstInMinute()).isNull();
        assertThat(r.getLastOutMinute()).isNull();
    }

    @Test
    void scenario7_approvedLeave() {
        DayResult r = DayCalculator.calculate(new DayInput(1L, MON, DayKind.WORKDAY, true, START, END, 60,
                List.of(), List.of()));
        assertThat(r.getStatusCode()).isEqualTo("LEAVE");
        assertThat(r.getWorkedMinutes()).isZero();
        assertThat(r.getMissingMinutes()).isZero();
    }

    @Test
    void scenario8_rejectedOrPendingLeave() {
        DayResult r = DayCalculator.calculate(workday(List.of()));
        assertThat(r.getStatusCode()).isEqualTo("ABSENT");
        assertThat(r.getMissingMinutes()).isEqualTo(480);
    }

    @Test
    void scenario9_weekendWork() {
        DayResult r = DayCalculator.calculate(new DayInput(1L, MON, DayKind.WEEKEND, false, null, null, 0,
                List.of(in(8, 0), out(12, 0)), List.of()));
        assertThat(r.getStatusCode()).isEqualTo("WEEKEND");
        assertThat(r.getWorkedMinutes()).isEqualTo(240);
        assertThat(r.getOvertimeMinutes()).isEqualTo(240);
        assertThat(r.getMissingMinutes()).isZero();
    }

    @Test
    void scenario10_holidayWork() {
        DayResult r = DayCalculator.calculate(new DayInput(1L, MON, DayKind.HOLIDAY, false, null, null, 0,
                List.of(in(8, 0), out(12, 0)), List.of()));
        assertThat(r.getStatusCode()).isEqualTo("HOLIDAY");
        assertThat(r.getWorkedMinutes()).isEqualTo(240);
        assertThat(r.getOvertimeMinutes()).isEqualTo(240);
        assertThat(r.getMissingMinutes()).isZero();
    }

    @Test
    void scenario11_nightShift() {
        LocalTime nightStart = LocalTime.of(22, 0);
        LocalTime nightEnd = LocalTime.of(6, 0);
        DayResult r = DayCalculator.calculate(new DayInput(1L, MON, DayKind.WORKDAY, false, nightStart, nightEnd, 0,
                List.of(in(22, 0), out(6, 0)), List.of()));
        assertThat(r.getStatusCode()).isEqualTo("PRESENT");
        assertThat(r.getWorkedMinutes()).isEqualTo(480);
        assertThat(r.getFirstInMinute()).isEqualTo(1320);
        assertThat(r.getLastOutMinute()).isEqualTo(360);
        assertThat(r.getMissingMinutes()).isZero();
        assertThat(r.getOvertimeMinutes()).isZero();
    }

    @Test
    void scenario12_duplicateScans() {
        DayResult r = DayCalculator.calculate(workday(List.of(in(8, 0), in(8, 1), out(17, 0), out(17, 0))));
        assertThat(r.getStatusCode()).isEqualTo("PRESENT");
        assertThat(r.getFirstInMinute()).isEqualTo(480);
        assertThat(r.getLastOutMinute()).isEqualTo(1020);
        assertThat(r.getWorkedMinutes()).isEqualTo(480);
    }

    @Test
    void scenario13_offlineReplay() {
        DayResult r = DayCalculator.calculate(workday(List.of(in(8, 0), out(17, 0))));
        assertThat(r.getStatusCode()).isEqualTo("PRESENT");
        assertThat(r.getWorkedMinutes()).isEqualTo(480);
    }

    @Test
    void scenario14_manualAdjustment() {
        DayInput input = workday(List.of(in(8, 0), out(17, 0)));
        DayInput withAdj = new DayInput(1L, MON, DayKind.WORKDAY, false, START, END, 60,
                input.events(), List.of(new AdjustmentInput("ADD_MINUTES", 30)));
        DayResult r = DayCalculator.calculate(withAdj);
        assertThat(r.getStatusCode()).isEqualTo("ADJUSTED");
        assertThat(r.getWorkedMinutes()).isEqualTo(510);
        assertThat(r.getAdjustmentMinutes()).isEqualTo(30);
    }

    @Test
    void scenario14b_setAbsentVariant() {
        DayInput input = workday(List.of(in(8, 0), out(17, 0)));
        DayInput withAdj = new DayInput(1L, MON, DayKind.WORKDAY, false, START, END, 60,
                input.events(), List.of(new AdjustmentInput("SET_ABSENT", 0)));
        DayResult r = DayCalculator.calculate(withAdj);
        assertThat(r.getStatusCode()).isEqualTo("ABSENT");
        assertThat(r.getWorkedMinutes()).isZero();
        assertThat(r.getMissingMinutes()).isEqualTo(480);
    }

    @Test
    void scenario15_approvalWorkflowApplied() {
        DayInput input = workday(List.of(in(8, 0), out(17, 0)));
        DayInput withAdj = new DayInput(1L, MON, DayKind.WORKDAY, false, START, END, 60,
                input.events(), List.of(new AdjustmentInput("ADD_OVERTIME", 60)));
        DayResult r = DayCalculator.calculate(withAdj);
        assertThat(r.getStatusCode()).isEqualTo("ADJUSTED");
        assertThat(r.getWorkedMinutes()).isEqualTo(480);
        assertThat(r.getOvertimeMinutes()).isEqualTo(60);
        assertThat(r.getAdjustmentMinutes()).isEqualTo(60);
    }

    @Test
    void scenario16_workdayOvertime() {
        DayResult r = DayCalculator.calculate(workday(List.of(in(8, 0), out(18, 0))));
        assertThat(r.getStatusCode()).isEqualTo("PRESENT");
        assertThat(r.getWorkedMinutes()).isEqualTo(540);
        assertThat(r.getOvertimeMinutes()).isEqualTo(60);
        assertThat(r.getLastOutMinute()).isEqualTo(1080);
    }
}

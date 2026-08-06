package com.pointagepro.payroll.entity;

import com.pointagepro.attendance.entity.AttendanceStatus;
import com.pointagepro.attendance.entity.AttendanceSummary;
import com.pointagepro.attendance.entity.DayType;
import com.pointagepro.attendance.entity.WorkSchedule;
import com.pointagepro.company.entity.Company;
import com.pointagepro.employee.entity.Employee;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Immutable, per-day copy of the attendance facts used by one payroll run
 * (freeze step at generation time), including the resolved schedule values
 * (scheduled_start_time / scheduled_end_time / scheduled_break_minutes) so the
 * run is self-contained and reproducible even after the schedule changes.
 * Payroll computes solely from this table; {@code attendance_summary} may be
 * recomputed later without ever touching a frozen run. Written once, never
 * updated; corrections go to a new run.
 */
@Entity
@Table(name = "payroll_attendance_snapshots",
        uniqueConstraints = @UniqueConstraint(name = "uk_pas",
                columnNames = {"payroll_id", "employee_id", "work_date"}))
@Getter
@Setter
@NoArgsConstructor
public class PayrollAttendanceSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payroll_id", nullable = false)
    private Long payrollId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "day_type_id")
    private DayType dayType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id")
    private WorkSchedule schedule;

    @Column(name = "scheduled_start_time")
    private LocalTime scheduledStartTime;

    @Column(name = "scheduled_end_time")
    private LocalTime scheduledEndTime;

    @Column(name = "scheduled_break_minutes", nullable = false)
    private Integer scheduledBreakMinutes = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "status_id")
    private AttendanceStatus status;

    @Column(name = "is_paid_leave", nullable = false)
    private Boolean isPaidLeave = false;

    @Column(name = "first_in")
    private LocalTime firstIn;

    @Column(name = "last_out")
    private LocalTime lastOut;

    @Column(name = "worked_minutes", nullable = false)
    private Integer workedMinutes = 0;

    @Column(name = "late_minutes", nullable = false)
    private Integer lateMinutes = 0;

    @Column(name = "early_exit_minutes", nullable = false)
    private Integer earlyExitMinutes = 0;

    @Column(name = "missing_minutes", nullable = false)
    private Integer missingMinutes = 0;

    @Column(name = "overtime_minutes", nullable = false)
    private Integer overtimeMinutes = 0;

    @Column(name = "netted_work_minutes", nullable = false)
    private Integer nettedWorkMinutes = 0;

    @Column(name = "adjustment_minutes", nullable = false)
    private Integer adjustmentMinutes = 0;

    @Column(name = "is_weekend", nullable = false)
    private Boolean isWeekend = false;

    @Column(name = "is_holiday", nullable = false)
    private Boolean isHoliday = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_summary_id")
    private AttendanceSummary sourceSummary;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}

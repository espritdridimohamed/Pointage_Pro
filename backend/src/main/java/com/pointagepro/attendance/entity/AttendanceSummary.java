package com.pointagepro.attendance.entity;

import com.pointagepro.auth.entity.User;
import com.pointagepro.company.entity.Company;
import com.pointagepro.employee.entity.Employee;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "attendance_summary",
        uniqueConstraints = @UniqueConstraint(name = "uk_summary_employee_date", columnNames = {"employee_id", "work_date"}))
@Getter
@Setter
@NoArgsConstructor
public class AttendanceSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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

    @Column(name = "is_weekend", nullable = false)
    private Boolean isWeekend = false;

    @Column(name = "is_holiday", nullable = false)
    private Boolean isHoliday = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "status_id")
    private AttendanceStatus status;

    @Column(name = "adjustment_minutes", nullable = false)
    private Integer adjustmentMinutes = 0;

    @Column(name = "computed_at")
    private LocalDateTime computedAt;

    @Column(name = "recompute_reason", length = 255)
    private String recomputeReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "computed_by_user_id")
    private User computedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

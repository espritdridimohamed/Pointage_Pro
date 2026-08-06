package com.pointagepro.attendance.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

@Entity
@Table(name = "work_schedule_lines",
        uniqueConstraints = @UniqueConstraint(name = "uk_schedule_lines", columnNames = {"schedule_id", "weekday"}))
@Getter
@Setter
@NoArgsConstructor
public class WorkScheduleLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id", nullable = false)
    private WorkSchedule schedule;

    @Column(nullable = false)
    private Integer weekday;

    @Column(name = "is_workday", nullable = false)
    private Boolean isWorkday = true;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Column(name = "break_minutes", nullable = false)
    private Integer breakMinutes = 0;
}

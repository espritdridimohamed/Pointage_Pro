package com.pointagepro.attendance.entity;

import com.pointagepro.company.entity.Company;
import com.pointagepro.employee.entity.Employee;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "attendance_events",
        uniqueConstraints = @UniqueConstraint(name = "uk_events_terminal_ref", columnNames = {"terminal_id", "external_ref"}))
@Getter
@Setter
@NoArgsConstructor
public class AttendanceEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "terminal_id")
    private Long terminalId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_type_id", nullable = false)
    private EventType eventType;

    @Column(name = "event_time", nullable = false)
    private LocalDateTime eventTime;

    @Column(name = "rfid_uid", length = 30)
    private String rfidUid;

    @Column(nullable = false, length = 20)
    private String source = "TERMINAL";

    @Column(name = "external_ref", length = 50)
    private String externalRef;

    @Column(name = "time_warning", nullable = false)
    private Boolean timeWarning = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}

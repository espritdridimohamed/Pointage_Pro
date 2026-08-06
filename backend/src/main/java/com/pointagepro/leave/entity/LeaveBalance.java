package com.pointagepro.leave.entity;

import com.pointagepro.employee.entity.Employee;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "leave_balances",
        uniqueConstraints = @UniqueConstraint(name = "uk_balance", columnNames = {"employee_id", "leave_type_id", "year"}))
@Getter
@Setter
@NoArgsConstructor
public class LeaveBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leave_type_id", nullable = false)
    private LeaveType leaveType;

    @Column(nullable = false)
    private Integer year;

    @Column(name = "entitlement_days", nullable = false, precision = 5, scale = 2)
    private BigDecimal entitlementDays = BigDecimal.ZERO;

    @Column(name = "taken_days", nullable = false, precision = 5, scale = 2)
    private BigDecimal takenDays = BigDecimal.ZERO;

    @Column(name = "carried_over_days", nullable = false, precision = 5, scale = 2)
    private BigDecimal carriedOverDays = BigDecimal.ZERO;

    @Column(name = "adjusted_days", nullable = false, precision = 5, scale = 2)
    private BigDecimal adjustedDays = BigDecimal.ZERO;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;
}

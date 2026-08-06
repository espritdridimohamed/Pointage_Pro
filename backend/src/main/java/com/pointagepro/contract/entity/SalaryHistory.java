package com.pointagepro.contract.entity;

import com.pointagepro.auth.entity.User;
import com.pointagepro.employee.entity.Employee;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "salary_history")
@Getter
@Setter
@NoArgsConstructor
public class SalaryHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id")
    private EmployeeContract contract;

    @Column(name = "old_amount", precision = 10, scale = 2)
    private BigDecimal oldAmount;

    @Column(name = "new_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal newAmount;

    @Column(name = "change_date", nullable = false)
    private LocalDate changeDate;

    @Column(length = 255)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by")
    private User changedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}

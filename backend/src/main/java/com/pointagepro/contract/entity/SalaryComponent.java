package com.pointagepro.contract.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "salary_components")
@Getter
@Setter
@NoArgsConstructor
public class SalaryComponent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", nullable = false)
    private EmployeeContract contract;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "component_type_id", nullable = false)
    private SalaryComponentType componentType;

    @Column(nullable = false, length = 100)
    private String label;

    @Column(precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "is_percentage", nullable = false)
    private Boolean isPercentage = false;

    @Column(name = "percentage_value", precision = 5, scale = 2)
    private BigDecimal percentageValue;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

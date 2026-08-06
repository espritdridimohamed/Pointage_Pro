package com.pointagepro.legal.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "cnss_rates")
@Getter
@Setter
@NoArgsConstructor
public class CnssRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Integer year;

    @Column(name = "employee_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal employeeRate;

    @Column(name = "employer_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal employerRate;

    @Column(name = "family_allocations_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal familyAllocationsRate;

    @Column(name = "ceiling_amount", precision = 12, scale = 2)
    private BigDecimal ceilingAmount;

    @Column(name = "active_from", nullable = false)
    private LocalDate activeFrom;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}

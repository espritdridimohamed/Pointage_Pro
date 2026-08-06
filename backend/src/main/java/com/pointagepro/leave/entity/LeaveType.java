package com.pointagepro.leave.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "leave_types")
@Getter
@Setter
@NoArgsConstructor
public class LeaveType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "is_paid", nullable = false)
    private Boolean isPaid = true;

    @Column(name = "default_days_per_year", precision = 5, scale = 2)
    private BigDecimal defaultDaysPerYear;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}

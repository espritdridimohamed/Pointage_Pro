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
@Table(name = "family_allowances")
@Getter
@Setter
@NoArgsConstructor
public class FamilyAllowance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Integer year;

    @Column(name = "max_children", nullable = false)
    private Integer maxChildren = 6;

    @Column(name = "amount_per_child", nullable = false, precision = 6, scale = 2)
    private BigDecimal amountPerChild;

    @Column(name = "active_from", nullable = false)
    private LocalDate activeFrom;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}

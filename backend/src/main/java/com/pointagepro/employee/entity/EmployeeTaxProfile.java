package com.pointagepro.employee.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "employee_tax_profiles",
        uniqueConstraints = @UniqueConstraint(name = "uk_tax_profile_employee_date", columnNames = {"employee_id", "valid_from"}))
@Getter
@Setter
@NoArgsConstructor
public class EmployeeTaxProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tax_situation_id", nullable = false)
    private TaxSituation taxSituation;

    @Column(name = "spouse_is_working", nullable = false)
    private Boolean spouseIsWorking = false;

    @Column(name = "number_of_children", nullable = false)
    private Integer numberOfChildren = 0;

    @Column(name = "number_of_disabled_children", nullable = false)
    private Integer numberOfDisabledChildren = 0;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

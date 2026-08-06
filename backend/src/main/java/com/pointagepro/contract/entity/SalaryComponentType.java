package com.pointagepro.contract.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "salary_component_types")
@Getter
@Setter
@NoArgsConstructor
public class SalaryComponentType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 100)
    private String label;

    @Column(nullable = false, length = 20)
    private String category = "BONUS";

    @Column(name = "is_subject_to_cnss", nullable = false)
    private Boolean isSubjectToCnss = true;

    @Column(name = "is_subject_to_irpp", nullable = false)
    private Boolean isSubjectToIrpp = true;

    @Column(name = "is_subject_to_css", nullable = false)
    private Boolean isSubjectToCss = false;
}

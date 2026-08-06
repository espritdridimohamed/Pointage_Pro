package com.pointagepro.employee.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class DependentResponse {

    private Long id;

    private String firstName;

    private String lastName;

    private String cin;

    private LocalDate birthDate;

    private Long relationshipId;

    private String relationshipCode;

    private Boolean taxDeductible;

    private LocalDateTime createdAt;
}

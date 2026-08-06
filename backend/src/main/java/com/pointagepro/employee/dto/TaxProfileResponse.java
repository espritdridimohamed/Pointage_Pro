package com.pointagepro.employee.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class TaxProfileResponse {

    private Long id;

    private Long taxSituationId;

    private String taxSituationCode;

    private Boolean spouseIsWorking;

    private Integer numberOfChildren;

    private Integer numberOfDisabledChildren;

    private LocalDate validFrom;

    private LocalDate validTo;
}

package com.pointagepro.organization.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class PositionResponse {

    private Long id;
    private String code;
    private String name;
    private Long departmentId;
    private String departmentName;
    private LocalDate validFrom;
    private LocalDate validTo;
    private long employeeCount;
}

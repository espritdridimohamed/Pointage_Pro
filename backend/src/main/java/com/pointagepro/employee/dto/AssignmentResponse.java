package com.pointagepro.employee.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class AssignmentResponse {

    private Long id;

    private Long departmentId;

    private String departmentName;

    private Long positionId;

    private String positionName;

    private Long locationId;

    private String locationName;

    private LocalDate validFrom;

    private LocalDate validTo;

    private LocalDateTime createdAt;
}

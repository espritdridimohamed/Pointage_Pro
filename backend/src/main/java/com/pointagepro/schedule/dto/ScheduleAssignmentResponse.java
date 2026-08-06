package com.pointagepro.schedule.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class ScheduleAssignmentResponse {

    private Long id;

    private Long scheduleId;

    private String scheduleCode;

    private String scheduleName;

    private LocalDate validFrom;

    private LocalDate validTo;

    private LocalDateTime createdAt;
}

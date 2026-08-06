package com.pointagepro.schedule.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
public class ScheduleLineResponse {

    private Integer weekday;

    private Boolean isWorkday;

    private LocalTime startTime;

    private LocalTime endTime;

    private Integer breakMinutes;
}

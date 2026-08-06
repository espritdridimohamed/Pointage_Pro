package com.pointagepro.schedule.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class WorkScheduleResponse {

    private Long id;

    private String code;

    private String name;

    private Boolean isDefault;

    private Boolean isActive;

    private int lineCount;

    private List<ScheduleLineResponse> lines;
}

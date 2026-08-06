package com.pointagepro.schedule.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class WorkScheduleRequest {

    private String code;

    private String name;

    private Boolean isDefault;

    private Boolean isActive;

    private List<ScheduleLineRequest> lines;
}

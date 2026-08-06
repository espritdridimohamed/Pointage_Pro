package com.pointagepro.organization.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class LocationResponse {

    private Long id;
    private String code;
    private String name;
    private String address;
    private Boolean isActive;
    private long employeeCount;
    private long terminalCount;
}

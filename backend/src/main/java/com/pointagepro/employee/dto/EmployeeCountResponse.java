package com.pointagepro.employee.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class EmployeeCountResponse {

    private long count;

    public EmployeeCountResponse(long count) {
        this.count = count;
    }
}

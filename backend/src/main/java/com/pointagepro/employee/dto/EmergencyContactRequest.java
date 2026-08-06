package com.pointagepro.employee.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class EmergencyContactRequest {

    private String fullName;

    private String relationship;

    private String phone;

    private String address;
}

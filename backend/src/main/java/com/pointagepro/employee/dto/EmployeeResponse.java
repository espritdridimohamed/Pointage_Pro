package com.pointagepro.employee.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Computed flat employee shape (EMPLOYEE_API_CONTRACT §2.5): identity + lookups + current
 * contract/salary/schedule/leave-balance values resolved from the normalized tables.
 */
@Getter
@Setter
@NoArgsConstructor
public class EmployeeResponse {

    private Long id;
    private String matricule;
    private String firstName;
    private String lastName;
    private String cin;
    private String passportNumber;
    private LocalDate birthDate;
    private String gender;
    private String maritalStatus;
    private String nationality;
    private String email;
    private String phone;
    private String address;
    private String city;
    private String department;
    private Long departmentId;
    private String position;
    private Long positionId;
    private Long locationId;
    private String contractType;
    private BigDecimal baseSalary;
    private BigDecimal primeTransport;
    private BigDecimal primePerformance;
    private BigDecimal primeOther;
    private BigDecimal totalPrimes;
    private String rfidUid;
    private String photo;
    private String weeklySchedule;
    private BigDecimal annualLeaveDays;
    private BigDecimal maternityLeaveDays;
    private BigDecimal paternityLeaveDays;
    private LocalDate hiringDate;
    private LocalDate exitDate;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

package com.pointagepro.employee.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Flat legacy-compatible employee payload (see EMPLOYEE_API_CONTRACT §2.5). Fields that no
 * longer exist as columns are translated by EmployeeService into contract / salary /
 * schedule / leave-balance / assignment rows.
 */
@Getter
@Setter
@NoArgsConstructor
public class EmployeeRequest {

    private String matricule;

    private String firstName;

    private String lastName;

    private String cin;

    private String passportNumber;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
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

    private String rfidUid;

    private String photo;

    private String weeklySchedule;

    private BigDecimal annualLeaveDays;

    private BigDecimal maternityLeaveDays;

    private BigDecimal paternityLeaveDays;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate hiringDate;

    private String status;
}

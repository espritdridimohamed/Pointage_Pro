package com.pointagepro.employee.dto;

import com.pointagepro.employee.Employee;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class EmployeeResponse {

    private Long id;
    private String matricule;
    private String firstName;
    private String lastName;
    private String phone;
    private String email;
    private String position;
    private String department;
    private String contractType;
    private String photo;
    private LocalDate birthDate;
    private String cin;
    private String address;
    private BigDecimal baseSalary;
    private BigDecimal primeTransport;
    private BigDecimal primePerformance;
    private BigDecimal primeOther;
    private BigDecimal totalPrimes;
    private String rfidUid;
    private String weeklySchedule;
    private Integer annualLeaveDays;
    private Integer maternityLeaveDays;
    private Integer paternityLeaveDays;
    private LocalDate hiringDate;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static EmployeeResponse fromEmployee(Employee employee) {
        EmployeeResponse response = new EmployeeResponse();
        response.id = employee.getId();
        response.matricule = employee.getMatricule();
        response.firstName = employee.getFirstName();
        response.lastName = employee.getLastName();
        response.phone = employee.getPhone();
        response.email = employee.getEmail();
        response.position = employee.getPosition();
        response.department = employee.getDepartment();
        response.contractType = employee.getContractType();
        response.photo = employee.getPhoto();
        response.birthDate = employee.getBirthDate();
        response.cin = employee.getCin();
        response.address = employee.getAddress();
        response.baseSalary = employee.getBaseSalary();
        response.primeTransport = employee.getPrimeTransport() != null ? employee.getPrimeTransport() : BigDecimal.ZERO;
        response.primePerformance = employee.getPrimePerformance() != null ? employee.getPrimePerformance() : BigDecimal.ZERO;
        response.primeOther = employee.getPrimeOther() != null ? employee.getPrimeOther() : BigDecimal.ZERO;
        response.totalPrimes = response.primeTransport.add(response.primePerformance).add(response.primeOther);
        response.rfidUid = employee.getRfidUid();
        response.weeklySchedule = employee.getWeeklySchedule();
        response.annualLeaveDays = employee.getAnnualLeaveDays();
        response.maternityLeaveDays = employee.getMaternityLeaveDays();
        response.paternityLeaveDays = employee.getPaternityLeaveDays();
        response.hiringDate = employee.getHiringDate();
        response.status = employee.getStatus();
        response.createdAt = employee.getCreatedAt();
        response.updatedAt = employee.getUpdatedAt();
        return response;
    }

    public Long getId() { return id; }
    public String getMatricule() { return matricule; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public String getPosition() { return position; }
    public String getDepartment() { return department; }
    public String getContractType() { return contractType; }
    public String getPhoto() { return photo; }
    public LocalDate getBirthDate() { return birthDate; }
    public String getCin() { return cin; }
    public String getAddress() { return address; }
    public BigDecimal getBaseSalary() { return baseSalary; }
    public BigDecimal getPrimeTransport() { return primeTransport; }
    public BigDecimal getPrimePerformance() { return primePerformance; }
    public BigDecimal getPrimeOther() { return primeOther; }
    public BigDecimal getTotalPrimes() { return totalPrimes; }
    public String getRfidUid() { return rfidUid; }
    public String getWeeklySchedule() { return weeklySchedule; }
    public Integer getAnnualLeaveDays() { return annualLeaveDays; }
    public Integer getMaternityLeaveDays() { return maternityLeaveDays; }
    public Integer getPaternityLeaveDays() { return paternityLeaveDays; }
    public LocalDate getHiringDate() { return hiringDate; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}

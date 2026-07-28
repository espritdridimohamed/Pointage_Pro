package com.pointagepro.employee.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public class EmployeeRequest {

    @Size(max = 20, message = "Matricule must not exceed 20 characters")
    private String matricule;

    @NotBlank(message = "First name is required")
    @Size(max = 50, message = "First name must not exceed 50 characters")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 50, message = "Last name must not exceed 50 characters")
    private String lastName;

    @Size(max = 20, message = "Phone must not exceed 20 characters")
    private String phone;

    @Email(message = "Invalid email format")
    @Size(max = 100, message = "Email must not exceed 100 characters")
    private String email;

    @Size(max = 100, message = "Position must not exceed 100 characters")
    private String position;

    @Size(max = 50, message = "Department must not exceed 50 characters")
    private String department;

    @Size(max = 30, message = "Contract type must not exceed 30 characters")
    private String contractType;

    private String photo;

    private LocalDate birthDate;

    @Size(max = 20, message = "CIN must not exceed 20 characters")
    private String cin;

    @Size(max = 255, message = "Address must not exceed 255 characters")
    private String address;

    @DecimalMin(value = "0.0", message = "Salary must be positive")
    private BigDecimal baseSalary;

    @DecimalMin(value = "0.0", message = "Transport prime must be positive")
    private BigDecimal primeTransport;

    @DecimalMin(value = "0.0", message = "Performance prime must be positive")
    private BigDecimal primePerformance;

    @DecimalMin(value = "0.0", message = "Other prime must be positive")
    private BigDecimal primeOther;

    @Size(max = 30, message = "RFID UID must not exceed 30 characters")
    private String rfidUid;

    private String weeklySchedule;

    private Integer annualLeaveDays;

    private Integer maternityLeaveDays;

    private Integer paternityLeaveDays;

    private LocalDate hiringDate;

    @Pattern(regexp = "ACTIF|INACTIF|CONGE", message = "Status must be ACTIF, INACTIF, or CONGE")
    private String status = "ACTIF";

    public String getMatricule() { return matricule; }
    public void setMatricule(String matricule) { this.matricule = matricule; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getContractType() { return contractType; }
    public void setContractType(String contractType) { this.contractType = contractType; }

    public String getPhoto() { return photo; }
    public void setPhoto(String photo) { this.photo = photo; }

    public LocalDate getBirthDate() { return birthDate; }
    public void setBirthDate(LocalDate birthDate) { this.birthDate = birthDate; }

    public String getCin() { return cin; }
    public void setCin(String cin) { this.cin = cin; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public BigDecimal getBaseSalary() { return baseSalary; }
    public void setBaseSalary(BigDecimal baseSalary) { this.baseSalary = baseSalary; }

    public BigDecimal getPrimeTransport() { return primeTransport; }
    public void setPrimeTransport(BigDecimal primeTransport) { this.primeTransport = primeTransport; }

    public BigDecimal getPrimePerformance() { return primePerformance; }
    public void setPrimePerformance(BigDecimal primePerformance) { this.primePerformance = primePerformance; }

    public BigDecimal getPrimeOther() { return primeOther; }
    public void setPrimeOther(BigDecimal primeOther) { this.primeOther = primeOther; }

    public String getRfidUid() { return rfidUid; }
    public void setRfidUid(String rfidUid) { this.rfidUid = rfidUid; }

    public String getWeeklySchedule() { return weeklySchedule; }
    public void setWeeklySchedule(String weeklySchedule) { this.weeklySchedule = weeklySchedule; }

    public Integer getAnnualLeaveDays() { return annualLeaveDays; }
    public void setAnnualLeaveDays(Integer annualLeaveDays) { this.annualLeaveDays = annualLeaveDays; }

    public Integer getMaternityLeaveDays() { return maternityLeaveDays; }
    public void setMaternityLeaveDays(Integer maternityLeaveDays) { this.maternityLeaveDays = maternityLeaveDays; }

    public Integer getPaternityLeaveDays() { return paternityLeaveDays; }
    public void setPaternityLeaveDays(Integer paternityLeaveDays) { this.paternityLeaveDays = paternityLeaveDays; }

    public LocalDate getHiringDate() { return hiringDate; }
    public void setHiringDate(LocalDate hiringDate) { this.hiringDate = hiringDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}

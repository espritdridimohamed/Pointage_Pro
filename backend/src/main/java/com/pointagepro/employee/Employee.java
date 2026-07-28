package com.pointagepro.employee;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "employees")
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String matricule;

    @Column(name = "first_name", nullable = false, length = 50)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 50)
    private String lastName;

    @Column(length = 20)
    private String phone;

    @Column(length = 100)
    private String email;

    @Column(length = 100)
    private String position;

    @Column(length = 50)
    private String department;

    @Column(name = "contract_type", length = 30)
    private String contractType;

    @Column(name = "photo", columnDefinition = "TEXT")
    private String photo;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(length = 20)
    private String cin;

    @Column(length = 255)
    private String address;

    @Column(name = "base_salary", precision = 10, scale = 2)
    private BigDecimal baseSalary;

    @Column(name = "prime_transport", precision = 10, scale = 2)
    private BigDecimal primeTransport = BigDecimal.ZERO;

    @Column(name = "prime_performance", precision = 10, scale = 2)
    private BigDecimal primePerformance = BigDecimal.ZERO;

    @Column(name = "prime_other", precision = 10, scale = 2)
    private BigDecimal primeOther = BigDecimal.ZERO;

    @Column(name = "rfid_uid", unique = true, length = 30)
    private String rfidUid;

    @Column(name = "weekly_schedule", columnDefinition = "TEXT")
    private String weeklySchedule;

    @Column(name = "annual_leave_days")
    private Integer annualLeaveDays;

    @Column(name = "maternity_leave_days")
    private Integer maternityLeaveDays;

    @Column(name = "paternity_leave_days")
    private Integer paternityLeaveDays;

    @Column(name = "hiring_date")
    private LocalDate hiringDate;

    @Column(nullable = false, length = 20)
    private String status = "ACTIF";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

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

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}

package com.pointagepro.settings;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "company_settings")
public class CompanySettings {

    @Id
    private Long id = 1L;

    // Entreprise
    @Column(name = "company_name", length = 100)
    private String companyName = "Sepab Agro";

    @Column(name = "company_sector", length = 100)
    private String companySector = "Agroalimentaire";

    @Column(name = "company_address", length = 255)
    private String companyAddress = "Rue Farhat Hached, Morneg, Ben Arous";

    @Column(name = "company_email", length = 100)
    private String companyEmail = "contact@sepab.tn";

    @Column(name = "company_phone", length = 20)
    private String companyPhone = "";

    @Column(name = "company_logo", columnDefinition = "MEDIUMTEXT")
    private String companyLogo;

    // Horaires de travail
    @Column(name = "work_start_time", length = 5)
    private String workStartTime = "08:00";

    @Column(name = "work_end_time", length = 5)
    private String workEndTime = "17:00";

    @Column(name = "work_days_per_week")
    private Integer workDaysPerWeek = 6;

    @Column(name = "work_days", length = 50)
    private String workDays = "LUN,MAR,MER,JEU,VEN,SAM";

    @Column(name = "late_grace_minutes")
    private Integer lateGraceMinutes = 15;

    @Column(name = "monthly_work_hours")
    private BigDecimal monthlyWorkHours = new BigDecimal("151.67");

    // Heures supplementaires
    @Column(name = "overtime_rate")
    private BigDecimal overtimeRate = new BigDecimal("1.5");

    @Column(name = "overtime_threshold_hours")
    private BigDecimal overtimeThresholdHours = new BigDecimal("8.00");

    // Paie
    @Column(name = "currency", length = 10)
    private String currency = "DT";

    @Column(name = "pay_day")
    private Integer payDay = 28;

    // CNSS & Assurance
    @Column(name = "cnss_rate")
    private BigDecimal cnssRate = new BigDecimal("11.26");

    @Column(name = "cnss_employer_rate")
    private BigDecimal cnssEmployerRate = new BigDecimal("16.57");

    @Column(name = "cnss_ceiling")
    private BigDecimal cnssCeiling = new BigDecimal("5173.085");

    @Column(name = "assurance_rate")
    private BigDecimal assuranceRate = new BigDecimal("0.761");

    // IR (Impot sur le Revenu)
    @Column(name = "ir_tranche1")
    private BigDecimal irTranche1 = new BigDecimal("5000");

    @Column(name = "ir_rate1")
    private BigDecimal irRate1 = BigDecimal.ZERO;

    @Column(name = "ir_tranche2")
    private BigDecimal irTranche2 = new BigDecimal("20000");

    @Column(name = "ir_rate2")
    private BigDecimal irRate2 = new BigDecimal("26");

    @Column(name = "ir_tranche3")
    private BigDecimal irTranche3 = new BigDecimal("30000");

    @Column(name = "ir_rate3")
    private BigDecimal irRate3 = new BigDecimal("28");

    @Column(name = "ir_tranche4")
    private BigDecimal irTranche4 = new BigDecimal("50000");

    @Column(name = "ir_rate4")
    private BigDecimal irRate4 = new BigDecimal("32");

    @Column(name = "ir_tranche5")
    private BigDecimal irTranche5 = new BigDecimal("999999");

    @Column(name = "ir_rate5")
    private BigDecimal irRate5 = new BigDecimal("35");

    @Column(name = "ir_abatement")
    private BigDecimal irAbatement = new BigDecimal("1080");

    // Preferences
    @Column(name = "language", length = 5)
    private String language = "fr";

    @Column(name = "theme", length = 20)
    private String theme = "light";

    // Timestamps
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

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public String getCompanySector() { return companySector; }
    public void setCompanySector(String companySector) { this.companySector = companySector; }
    public String getCompanyAddress() { return companyAddress; }
    public void setCompanyAddress(String companyAddress) { this.companyAddress = companyAddress; }
    public String getCompanyEmail() { return companyEmail; }
    public void setCompanyEmail(String companyEmail) { this.companyEmail = companyEmail; }
    public String getCompanyPhone() { return companyPhone; }
    public void setCompanyPhone(String companyPhone) { this.companyPhone = companyPhone; }
    public String getCompanyLogo() { return companyLogo; }
    public void setCompanyLogo(String companyLogo) { this.companyLogo = companyLogo; }

    public String getWorkStartTime() { return workStartTime; }
    public void setWorkStartTime(String workStartTime) { this.workStartTime = workStartTime; }
    public String getWorkEndTime() { return workEndTime; }
    public void setWorkEndTime(String workEndTime) { this.workEndTime = workEndTime; }
    public Integer getWorkDaysPerWeek() { return workDaysPerWeek; }
    public void setWorkDaysPerWeek(Integer workDaysPerWeek) { this.workDaysPerWeek = workDaysPerWeek; }
    public String getWorkDays() { return workDays; }
    public void setWorkDays(String workDays) { this.workDays = workDays; }
    public Integer getLateGraceMinutes() { return lateGraceMinutes; }
    public void setLateGraceMinutes(Integer lateGraceMinutes) { this.lateGraceMinutes = lateGraceMinutes; }
    public BigDecimal getMonthlyWorkHours() { return monthlyWorkHours; }
    public void setMonthlyWorkHours(BigDecimal monthlyWorkHours) { this.monthlyWorkHours = monthlyWorkHours; }

    public BigDecimal getOvertimeRate() { return overtimeRate; }
    public void setOvertimeRate(BigDecimal overtimeRate) { this.overtimeRate = overtimeRate; }
    public BigDecimal getOvertimeThresholdHours() { return overtimeThresholdHours; }
    public void setOvertimeThresholdHours(BigDecimal overtimeThresholdHours) { this.overtimeThresholdHours = overtimeThresholdHours; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public Integer getPayDay() { return payDay; }
    public void setPayDay(Integer payDay) { this.payDay = payDay; }

    public BigDecimal getCnssRate() { return cnssRate; }
    public void setCnssRate(BigDecimal cnssRate) { this.cnssRate = cnssRate; }
    public BigDecimal getCnssEmployerRate() { return cnssEmployerRate; }
    public void setCnssEmployerRate(BigDecimal cnssEmployerRate) { this.cnssEmployerRate = cnssEmployerRate; }
    public BigDecimal getCnssCeiling() { return cnssCeiling; }
    public void setCnssCeiling(BigDecimal cnssCeiling) { this.cnssCeiling = cnssCeiling; }
    public BigDecimal getAssuranceRate() { return assuranceRate; }
    public void setAssuranceRate(BigDecimal assuranceRate) { this.assuranceRate = assuranceRate; }

    public BigDecimal getIrTranche1() { return irTranche1; }
    public void setIrTranche1(BigDecimal irTranche1) { this.irTranche1 = irTranche1; }
    public BigDecimal getIrRate1() { return irRate1; }
    public void setIrRate1(BigDecimal irRate1) { this.irRate1 = irRate1; }
    public BigDecimal getIrTranche2() { return irTranche2; }
    public void setIrTranche2(BigDecimal irTranche2) { this.irTranche2 = irTranche2; }
    public BigDecimal getIrRate2() { return irRate2; }
    public void setIrRate2(BigDecimal irRate2) { this.irRate2 = irRate2; }
    public BigDecimal getIrTranche3() { return irTranche3; }
    public void setIrTranche3(BigDecimal irTranche3) { this.irTranche3 = irTranche3; }
    public BigDecimal getIrRate3() { return irRate3; }
    public void setIrRate3(BigDecimal irRate3) { this.irRate3 = irRate3; }
    public BigDecimal getIrTranche4() { return irTranche4; }
    public void setIrTranche4(BigDecimal irTranche4) { this.irTranche4 = irTranche4; }
    public BigDecimal getIrRate4() { return irRate4; }
    public void setIrRate4(BigDecimal irRate4) { this.irRate4 = irRate4; }
    public BigDecimal getIrTranche5() { return irTranche5; }
    public void setIrTranche5(BigDecimal irTranche5) { this.irTranche5 = irTranche5; }
    public BigDecimal getIrRate5() { return irRate5; }
    public void setIrRate5(BigDecimal irRate5) { this.irRate5 = irRate5; }
    public BigDecimal getIrAbatement() { return irAbatement; }
    public void setIrAbatement(BigDecimal irAbatement) { this.irAbatement = irAbatement; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public String getTheme() { return theme; }
    public void setTheme(String theme) { this.theme = theme; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}

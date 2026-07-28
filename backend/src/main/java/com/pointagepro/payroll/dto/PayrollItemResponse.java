package com.pointagepro.payroll.dto;

import com.pointagepro.employee.Employee;
import com.pointagepro.payroll.PayrollItem;
import java.math.BigDecimal;

public class PayrollItemResponse {

    private Long id;
    private Long employeeId;
    private String firstName;
    private String lastName;
    private String position;
    private String department;
    private String contractType;
    private String photo;
    private String initials;
    private String avatarColor;
    private BigDecimal baseSalary;
    private BigDecimal primeTransport;
    private BigDecimal primePerformance;
    private BigDecimal primeOther;
    private BigDecimal overtimeHours;
    private BigDecimal overtimeAmount;
    private BigDecimal totalGross;
    private BigDecimal cnssDeduction;
    private BigDecimal assuranceDeduction;
    private BigDecimal irDeduction;
    private BigDecimal lateDeduction;
    private BigDecimal absenceDeduction;
    private BigDecimal missingHours;
    private BigDecimal missingHoursDeduction;
    private BigDecimal absenceHours;
    private BigDecimal totalDeductions;
    private BigDecimal netSalary;
    private Integer daysWorked;
    private Integer daysAbsent;
    private Integer lateMinutes;
    private Integer totalOvertimeMinutes;
    private BigDecimal hourlyRate;
    private BigDecimal minuteRate;
    private String status;

    private static final String[] AVATAR_COLORS = {
        "#2563EB", "#10B981", "#EC4899", "#F59E0B", "#8B5CF6", "#06B6D4"
    };

    public static PayrollItemResponse fromEntity(PayrollItem item, Employee employee) {
        PayrollItemResponse r = new PayrollItemResponse();
        r.id = item.getId();
        r.employeeId = item.getEmployeeId();
        r.baseSalary = item.getBaseSalary();
        r.primeTransport = item.getPrimeTransport();
        r.primePerformance = item.getPrimePerformance();
        r.primeOther = item.getPrimeOther();
        r.overtimeHours = item.getOvertimeHours();
        r.overtimeAmount = item.getOvertimeAmount();
        r.totalGross = item.getTotalGross();
        r.cnssDeduction = item.getCnssDeduction();
        r.assuranceDeduction = item.getAssuranceDeduction();
        r.irDeduction = item.getIrDeduction();
        r.lateDeduction = item.getLateDeduction();
        r.absenceDeduction = item.getAbsenceDeduction();
        r.missingHours = item.getMissingHours();
        r.missingHoursDeduction = item.getMissingHoursDeduction();
        r.absenceHours = item.getAbsenceHours();
        r.totalDeductions = item.getTotalDeductions();
        r.netSalary = item.getNetSalary();
        r.daysWorked = item.getDaysWorked();
        r.daysAbsent = item.getDaysAbsent();
        r.lateMinutes = item.getLateMinutes();
        r.totalOvertimeMinutes = item.getTotalOvertimeMinutes();
        r.hourlyRate = item.getHourlyRate();
        r.minuteRate = item.getHourlyRate() != null
                ? item.getHourlyRate().divide(BigDecimal.valueOf(60), 4, java.math.RoundingMode.HALF_UP)
                : null;
        r.status = item.getStatus();

        if (employee != null) {
            r.firstName = employee.getFirstName();
            r.lastName = employee.getLastName();
            r.position = employee.getPosition();
            r.department = employee.getDepartment();
            r.contractType = employee.getContractType();
            r.photo = employee.getPhoto();
            r.initials = (employee.getFirstName().substring(0, 1) +
                    employee.getLastName().substring(0, 1)).toUpperCase();
            long colorIndex = Math.abs(employee.getId()) % AVATAR_COLORS.length;
            r.avatarColor = AVATAR_COLORS[(int) colorIndex];
        }
        return r;
    }

    public Long getId() { return id; }
    public Long getEmployeeId() { return employeeId; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getPosition() { return position; }
    public String getDepartment() { return department; }
    public String getContractType() { return contractType; }
    public String getPhoto() { return photo; }
    public String getInitials() { return initials; }
    public String getAvatarColor() { return avatarColor; }
    public BigDecimal getBaseSalary() { return baseSalary; }
    public BigDecimal getPrimeTransport() { return primeTransport; }
    public BigDecimal getPrimePerformance() { return primePerformance; }
    public BigDecimal getPrimeOther() { return primeOther; }
    public BigDecimal getOvertimeHours() { return overtimeHours; }
    public BigDecimal getOvertimeAmount() { return overtimeAmount; }
    public BigDecimal getTotalGross() { return totalGross; }
    public BigDecimal getCnssDeduction() { return cnssDeduction; }
    public BigDecimal getAssuranceDeduction() { return assuranceDeduction; }
    public BigDecimal getIrDeduction() { return irDeduction; }
    public BigDecimal getLateDeduction() { return lateDeduction; }
    public BigDecimal getAbsenceDeduction() { return absenceDeduction; }
    public BigDecimal getMissingHours() { return missingHours; }
    public BigDecimal getMissingHoursDeduction() { return missingHoursDeduction; }
    public BigDecimal getAbsenceHours() { return absenceHours; }
    public BigDecimal getTotalDeductions() { return totalDeductions; }
    public BigDecimal getNetSalary() { return netSalary; }
    public Integer getDaysWorked() { return daysWorked; }
    public Integer getDaysAbsent() { return daysAbsent; }
    public Integer getLateMinutes() { return lateMinutes; }
    public Integer getTotalOvertimeMinutes() { return totalOvertimeMinutes; }
    public BigDecimal getHourlyRate() { return hourlyRate; }
    public BigDecimal getMinuteRate() { return minuteRate; }
    public String getStatus() { return status; }
}

package com.pointagepro.leave.dto;

import com.pointagepro.leave.entity.LeaveBalance;

import java.math.BigDecimal;

/**
 * Per-year balance of one tracked leave type: availableDays = entitlementDays +
 * carriedOverDays + adjustedDays - takenDays.
 */
public class LeaveBalanceResponse {

    private Long employeeId;
    private String leaveTypeCode;
    private String leaveTypeName;
    private Integer year;
    private BigDecimal entitlementDays;
    private BigDecimal carriedOverDays;
    private BigDecimal adjustedDays;
    private BigDecimal takenDays;
    private BigDecimal availableDays;

    public LeaveBalanceResponse() {
    }

    public static LeaveBalanceResponse from(LeaveBalance balance) {
        LeaveBalanceResponse dto = new LeaveBalanceResponse();
        dto.employeeId = balance.getEmployee().getId();
        dto.leaveTypeCode = balance.getLeaveType().getCode();
        dto.leaveTypeName = balance.getLeaveType().getName();
        dto.year = balance.getYear();
        dto.entitlementDays = balance.getEntitlementDays();
        dto.carriedOverDays = balance.getCarriedOverDays();
        dto.adjustedDays = balance.getAdjustedDays();
        dto.takenDays = balance.getTakenDays();
        dto.availableDays = balance.getEntitlementDays()
                .add(balance.getCarriedOverDays())
                .add(balance.getAdjustedDays())
                .subtract(balance.getTakenDays());
        return dto;
    }

    public Long getEmployeeId() { return employeeId; }
    public String getLeaveTypeCode() { return leaveTypeCode; }
    public String getLeaveTypeName() { return leaveTypeName; }
    public Integer getYear() { return year; }
    public BigDecimal getEntitlementDays() { return entitlementDays; }
    public BigDecimal getCarriedOverDays() { return carriedOverDays; }
    public BigDecimal getAdjustedDays() { return adjustedDays; }
    public BigDecimal getTakenDays() { return takenDays; }
    public BigDecimal getAvailableDays() { return availableDays; }
}

package com.pointagepro.payroll.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class PayrollResponse {

    private Long id;
    private int month;
    private int year;
    private String status;
    private BigDecimal totalGross;
    private BigDecimal totalDeductions;
    private BigDecimal totalNet;
    private int employeeCount;
    private LocalDateTime createdAt;
    private List<PayrollItemResponse> items;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public int getMonth() { return month; }
    public void setMonth(int month) { this.month = month; }
    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public BigDecimal getTotalGross() { return totalGross; }
    public void setTotalGross(BigDecimal totalGross) { this.totalGross = totalGross; }
    public BigDecimal getTotalDeductions() { return totalDeductions; }
    public void setTotalDeductions(BigDecimal totalDeductions) { this.totalDeductions = totalDeductions; }
    public BigDecimal getTotalNet() { return totalNet; }
    public void setTotalNet(BigDecimal totalNet) { this.totalNet = totalNet; }
    public int getEmployeeCount() { return employeeCount; }
    public void setEmployeeCount(int employeeCount) { this.employeeCount = employeeCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public List<PayrollItemResponse> getItems() { return items; }
    public void setItems(List<PayrollItemResponse> items) { this.items = items; }
}

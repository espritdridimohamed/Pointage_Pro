package com.pointagepro.payroll.dto;

import com.pointagepro.payroll.entity.PayrollItemComponent;

import java.math.BigDecimal;

public class PayrollComponentResponse {

    private String componentTypeCode;
    private String label;
    private String category;
    private BigDecimal amount;
    private boolean isPercentage;
    private BigDecimal percentageValue;
    private Integer sortOrder;

    public PayrollComponentResponse() {
    }

    public static PayrollComponentResponse from(PayrollItemComponent c) {
        PayrollComponentResponse dto = new PayrollComponentResponse();
        dto.componentTypeCode = c.getComponentType() != null ? c.getComponentType().getCode() : null;
        dto.label = c.getLabel();
        dto.category = c.getCategory();
        dto.amount = c.getAmount();
        dto.isPercentage = Boolean.TRUE.equals(c.getIsPercentage());
        dto.percentageValue = c.getPercentageValue();
        dto.sortOrder = c.getSortOrder();
        return dto;
    }

    public String getComponentTypeCode() { return componentTypeCode; }
    public String getLabel() { return label; }
    public String getCategory() { return category; }
    public BigDecimal getAmount() { return amount; }
    public boolean isPercentage() { return isPercentage; }
    public BigDecimal getPercentageValue() { return percentageValue; }
    public Integer getSortOrder() { return sortOrder; }
}

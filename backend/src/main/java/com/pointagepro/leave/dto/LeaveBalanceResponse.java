package com.pointagepro.leave.dto;

public class LeaveBalanceResponse {

    private String type;
    private Long total;
    private long used;
    private Long remaining;
    private String color;

    public LeaveBalanceResponse() {}

    public LeaveBalanceResponse(String type, Long total, long used, Long remaining, String color) {
        this.type = type;
        this.total = total;
        this.used = used;
        this.remaining = remaining;
        this.color = color;
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public Long getTotal() { return total; }
    public void setTotal(Long total) { this.total = total; }

    public long getUsed() { return used; }
    public void setUsed(long used) { this.used = used; }

    public Long getRemaining() { return remaining; }
    public void setRemaining(Long remaining) { this.remaining = remaining; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
}

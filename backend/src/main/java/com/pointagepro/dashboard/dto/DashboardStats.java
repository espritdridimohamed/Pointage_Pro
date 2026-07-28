package com.pointagepro.dashboard.dto;

public class DashboardStats {

    private long totalEmployees;
    private long presentToday;
    private long absentToday;
    private long lateToday;
    private long pendingLeaves;

    public DashboardStats(long totalEmployees, long presentToday, long absentToday, long lateToday, long pendingLeaves) {
        this.totalEmployees = totalEmployees;
        this.presentToday = presentToday;
        this.absentToday = absentToday;
        this.lateToday = lateToday;
        this.pendingLeaves = pendingLeaves;
    }

    public long getTotalEmployees() { return totalEmployees; }
    public long getPresentToday() { return presentToday; }
    public long getAbsentToday() { return absentToday; }
    public long getLateToday() { return lateToday; }
    public long getPendingLeaves() { return pendingLeaves; }
}

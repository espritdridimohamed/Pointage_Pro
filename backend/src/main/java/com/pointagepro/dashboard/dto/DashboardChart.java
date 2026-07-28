package com.pointagepro.dashboard.dto;

import java.util.List;

public class DashboardChart {

    private List<String> labels;
    private List<Long> present;
    private List<Long> absent;
    private List<Long> late;
    private String weekLabel;
    private int totalEmployees;

    public DashboardChart(List<String> labels, List<Long> present, List<Long> absent, List<Long> late, String weekLabel, int totalEmployees) {
        this.labels = labels;
        this.present = present;
        this.absent = absent;
        this.late = late;
        this.weekLabel = weekLabel;
        this.totalEmployees = totalEmployees;
    }

    public List<String> getLabels() { return labels; }
    public List<Long> getPresent() { return present; }
    public List<Long> getAbsent() { return absent; }
    public List<Long> getLate() { return late; }
    public String getWeekLabel() { return weekLabel; }
    public int getTotalEmployees() { return totalEmployees; }
}

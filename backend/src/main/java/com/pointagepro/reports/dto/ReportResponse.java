package com.pointagepro.reports.dto;

import java.util.List;

public class ReportResponse {

    private List<String> labels;
    private List<Integer> presence;
    private List<Integer> retards;
    private List<Long> masse;
    private List<Double> overtimeHours;
    private int totalEmployees;
    private List<AbsenceBreakdown> absences;
    private List<EmployeeAttendanceStats> employeeStats;

    public List<String> getLabels() { return labels; }
    public void setLabels(List<String> labels) { this.labels = labels; }
    public List<Integer> getPresence() { return presence; }
    public void setPresence(List<Integer> presence) { this.presence = presence; }
    public List<Integer> getRetards() { return retards; }
    public void setRetards(List<Integer> retards) { this.retards = retards; }
    public List<Long> getMasse() { return masse; }
    public void setMasse(List<Long> masse) { this.masse = masse; }
    public List<Double> getOvertimeHours() { return overtimeHours; }
    public void setOvertimeHours(List<Double> overtimeHours) { this.overtimeHours = overtimeHours; }
    public int getTotalEmployees() { return totalEmployees; }
    public void setTotalEmployees(int totalEmployees) { this.totalEmployees = totalEmployees; }
    public List<AbsenceBreakdown> getAbsences() { return absences; }
    public void setAbsences(List<AbsenceBreakdown> absences) { this.absences = absences; }
    public List<EmployeeAttendanceStats> getEmployeeStats() { return employeeStats; }
    public void setEmployeeStats(List<EmployeeAttendanceStats> employeeStats) { this.employeeStats = employeeStats; }

    public static class AbsenceBreakdown {
        private String name;
        private long value;
        private String color;

        public AbsenceBreakdown(String name, long value, String color) {
            this.name = name;
            this.value = value;
            this.color = color;
        }

        public String getName() { return name; }
        public long getValue() { return value; }
        public String getColor() { return color; }
    }
}

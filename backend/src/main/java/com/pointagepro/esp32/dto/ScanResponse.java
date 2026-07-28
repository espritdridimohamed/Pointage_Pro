package com.pointagepro.esp32.dto;

public class ScanResponse {

    private boolean success;
    private String action;
    private String employeeName;
    private String matricule;
    private String message;
    private String time;

    public ScanResponse() {}

    public ScanResponse(boolean success, String action, String employeeName,
                        String matricule, String message, String time) {
        this.success = success;
        this.action = action;
        this.employeeName = employeeName;
        this.matricule = matricule;
        this.message = message;
        this.time = time;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }
    public String getMatricule() { return matricule; }
    public void setMatricule(String matricule) { this.matricule = matricule; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }
}

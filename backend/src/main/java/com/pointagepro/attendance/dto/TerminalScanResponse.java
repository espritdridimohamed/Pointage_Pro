package com.pointagepro.attendance.dto;

/**
 * Flat body returned to the ESP32 (not the ApiResponse envelope). Field names must match
 * firmware/src/api_client.cpp (success, action, employeeName, matricule, message, time).
 */
public class TerminalScanResponse {

    private boolean success;
    private String action;
    private String employeeName;
    private String matricule;
    private String message;
    private String time;

    public TerminalScanResponse() {
    }

    public static TerminalScanResponse success(String action, String employeeName, String matricule,
                                              String message, String time) {
        TerminalScanResponse r = new TerminalScanResponse();
        r.success = true;
        r.action = action;
        r.employeeName = employeeName;
        r.matricule = matricule;
        r.message = message;
        r.time = time;
        return r;
    }

    public static TerminalScanResponse rejected(String message) {
        TerminalScanResponse r = new TerminalScanResponse();
        r.success = false;
        r.action = "";
        r.employeeName = "";
        r.matricule = "";
        r.message = message;
        r.time = "";
        return r;
    }

    public boolean isSuccess() { return success; }
    public String getAction() { return action; }
    public String getEmployeeName() { return employeeName; }
    public String getMatricule() { return matricule; }
    public String getMessage() { return message; }
    public String getTime() { return time; }
}

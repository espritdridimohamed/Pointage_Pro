package com.pointagepro.esp32;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "scan_events")
public class ScanEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id")
    private String deviceId;

    @Column(name = "rfid_uid")
    private String rfidUid;

    @Column(name = "employee_id")
    private Long employeeId;

    @Column(name = "employee_name")
    private String employeeName;

    @Column(name = "matricule")
    private String matricule;

    @Column(name = "action")
    private String action;

    @Column(name = "scanned_at", nullable = false)
    private LocalDateTime scannedAt;

    @PrePersist
    protected void onCreate() {
        if (scannedAt == null) scannedAt = LocalDateTime.now();
    }

    public ScanEvent() {}

    public ScanEvent(String deviceId, String rfidUid, Long employeeId,
                     String employeeName, String matricule, String action) {
        this.deviceId = deviceId;
        this.rfidUid = rfidUid;
        this.employeeId = employeeId;
        this.employeeName = employeeName;
        this.matricule = matricule;
        this.action = action;
        this.scannedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getDeviceId() { return deviceId; }
    public String getRfidUid() { return rfidUid; }
    public Long getEmployeeId() { return employeeId; }
    public String getEmployeeName() { return employeeName; }
    public String getMatricule() { return matricule; }
    public String getAction() { return action; }
    public LocalDateTime getScannedAt() { return scannedAt; }
}

package com.pointagepro.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of POST /esp32/scan. Timestamp is ISO-8601 local datetime; it is optional because
 * the device omits it on live scans (server time is used) and sends it only for offline sync.
 */
public class TerminalScanRequest {

    @NotBlank(message = "rfidUid is required")
    @Size(max = 30, message = "rfidUid must be at most 30 characters")
    private String rfidUid;

    @Size(max = 50, message = "externalRef must be at most 50 characters")
    private String externalRef;

    @Size(max = 30, message = "timestamp must be at most 30 characters")
    private String timestamp;

    @Pattern(regexp = "IN|OUT", message = "eventType must be IN or OUT")
    private String eventType;

    @Size(max = 50, message = "deviceSerial must be at most 50 characters")
    private String deviceSerial;

    public TerminalScanRequest() {
    }

    public TerminalScanRequest(String rfidUid, String externalRef, String timestamp,
                               String eventType, String deviceSerial) {
        this.rfidUid = rfidUid;
        this.externalRef = externalRef;
        this.timestamp = timestamp;
        this.eventType = eventType;
        this.deviceSerial = deviceSerial;
    }

    public String getRfidUid() { return rfidUid; }
    public String getExternalRef() { return externalRef; }
    public String getTimestamp() { return timestamp; }
    public String getEventType() { return eventType; }
    public String getDeviceSerial() { return deviceSerial; }
}

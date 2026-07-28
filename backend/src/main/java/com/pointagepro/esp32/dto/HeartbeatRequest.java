package com.pointagepro.esp32.dto;

public class HeartbeatRequest {
    private String deviceId = "ESP32-001";
    private String ipAddress;
    private Integer rssi;
    private String firmwareVersion;
    private Integer freeMemory;
    private Long uptimeSeconds;

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public Integer getRssi() { return rssi; }
    public void setRssi(Integer rssi) { this.rssi = rssi; }
    public String getFirmwareVersion() { return firmwareVersion; }
    public void setFirmwareVersion(String firmwareVersion) { this.firmwareVersion = firmwareVersion; }
    public Integer getFreeMemory() { return freeMemory; }
    public void setFreeMemory(Integer freeMemory) { this.freeMemory = freeMemory; }
    public Long getUptimeSeconds() { return uptimeSeconds; }
    public void setUptimeSeconds(Long uptimeSeconds) { this.uptimeSeconds = uptimeSeconds; }
}

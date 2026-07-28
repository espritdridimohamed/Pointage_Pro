package com.pointagepro.auth.dto;

public class NotificationPreferencesRequest {
    private boolean emailNotifications;
    private boolean browserNotifications;
    private boolean dailySummary;

    public boolean isEmailNotifications() { return emailNotifications; }
    public void setEmailNotifications(boolean emailNotifications) { this.emailNotifications = emailNotifications; }
    public boolean isBrowserNotifications() { return browserNotifications; }
    public void setBrowserNotifications(boolean browserNotifications) { this.browserNotifications = browserNotifications; }
    public boolean isDailySummary() { return dailySummary; }
    public void setDailySummary(boolean dailySummary) { this.dailySummary = dailySummary; }
}

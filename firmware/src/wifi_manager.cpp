#include "wifi_manager.h"
#include <WiFi.h>
#include "config.h"

void WiFiManager::begin() {
    WiFi.mode(WIFI_STA);
    WiFi.setHostname("PointagePro-Terminal");
}

bool WiFiManager::connect() {
    if (WiFi.status() == WL_CONNECTED) return true;

    Serial.println("[WiFi] Connecting to " + String(WIFI_SSID));
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

    unsigned long start = millis();
    while (WiFi.status() != WL_CONNECTED && millis() - start < WIFI_TIMEOUT_MS) {
        delay(100);
    }

    if (WiFi.status() == WL_CONNECTED) {
        Serial.println("[WiFi] Connected: " + WiFi.localIP().toString());
        _wasConnected = true;
        return true;
    }

    Serial.println("[WiFi] Connection failed");
    return false;
}

bool WiFiManager::isConnected() {
    return WiFi.status() == WL_CONNECTED;
}

void WiFiManager::loop() {
    bool currentlyConnected = WiFi.status() == WL_CONNECTED;

    if (!currentlyConnected && _wasConnected) {
        Serial.println("[WiFi] Lost connection");
        _wasConnected = false;
        _lastReconnectAttempt = millis();
    }

    if (!currentlyConnected && !_wasConnected) {
        if (millis() - _lastReconnectAttempt > RECONNECT_INTERVAL) {
            Serial.println("[WiFi] Attempting reconnect...");
            WiFi.reconnect();
            _lastReconnectAttempt = millis();
        }
    }

    if (currentlyConnected && !_wasConnected) {
        Serial.println("[WiFi] Reconnected: " + WiFi.localIP().toString());
        _wasConnected = true;
    }
}

void WiFiManager::disconnect() {
    WiFi.disconnect();
    _wasConnected = false;
}

String WiFiManager::getLocalIP() {
    return WiFi.localIP().toString();
}

int WiFiManager::getSignalStrength() {
    return WiFi.RSSI();
}

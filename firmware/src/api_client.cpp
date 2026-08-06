#include "api_client.h"
#include "config.h"
#include <WiFi.h>
#include <HTTPClient.h>
#include <ArduinoJson.h>

void ApiClient::begin() {
    _baseUrl = API_BASE_URL;
    _offlineMode = false;
}

void ApiClient::setOfflineMode(bool offline) {
    _offlineMode = offline;
}

bool ApiClient::isOfflineMode() {
    return _offlineMode;
}

ScanResponse ApiClient::scan(const String& rfidUid, const String& externalRef, const String& timestamp) {
    ScanResponse response;
    response.success = false;

    if (WiFi.status() != WL_CONNECTED) {
        _offlineMode = true;
        response.message = "Hors ligne";
        return response;
    }

    JsonDocument doc;
    doc["rfidUid"] = rfidUid;
    if (externalRef.length() > 0) {
        doc["externalRef"] = externalRef;
    }
    if (timestamp.length() > 0) {
        doc["timestamp"] = timestamp;
    }
    String body;
    serializeJson(doc, body);

    String result = httpPost(API_SCAN_ENDPOINT, body);

    if (result.isEmpty()) {
        response.message = "Timeout serveur";
        return response;
    }

    JsonDocument resDoc;
    DeserializationError err = deserializeJson(resDoc, result);
    if (err) {
        response.message = "Reponse invalide";
        return response;
    }

    response.success = resDoc["success"] | false;
    response.action = resDoc["action"] | "";
    response.employeeName = resDoc["employeeName"] | "";
    response.matricule = resDoc["matricule"] | "";
    response.message = resDoc["message"] | "";
    response.time = resDoc["time"] | "";

    return response;
}

bool ApiClient::heartbeat() {
    if (WiFi.status() != WL_CONNECTED) return false;

    JsonDocument doc;
    doc["deviceId"] = DEVICE_SERIAL;
    doc["ipAddress"] = WiFi.localIP().toString();
    doc["rssi"] = WiFi.RSSI();
    doc["firmwareVersion"] = "1.0.0";
    doc["freeMemory"] = ESP.getFreeHeap();
    doc["uptimeSeconds"] = millis() / 1000;
    String body;
    serializeJson(doc, body);

    String result = httpPost(API_HEARTBEAT_EP, body);
    return !result.isEmpty();
}

String ApiClient::httpPost(const String& endpoint, const String& body) {
    HTTPClient http;
    String url = _baseUrl + endpoint;

    http.begin(url);
    http.addHeader("Content-Type", "application/json");
    http.addHeader("X-API-Key", API_KEY);
    http.setTimeout(API_TIMEOUT_MS);

    int httpCode = http.POST(body);
    String payload = "";

    if (httpCode > 0) {
        payload = http.getString();
    } else {
        Serial.println("[API] HTTP error: " + String(httpCode));
    }

    http.end();
    return payload;
}

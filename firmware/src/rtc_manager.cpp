#include "rtc_manager.h"
#include "config.h"
#include <Wire.h>
#include <RTClib.h>
#include <time.h>
#include <WiFi.h>

static RTC_DS3231 _rtc;

void RtcManager::begin() {
    Wire.begin(PIN_SDA, PIN_SCL);

    if (!_rtc.begin()) {
        Serial.println("[RTC] DS3231 not found");
        _ready = false;
        return;
    }

    if (_rtc.lostPower()) {
        Serial.println("[RTC] Lost power, needs sync");
    }

    _ready = true;
    Serial.println("[RTC] DS3231 OK");
}

String RtcManager::getTime() {
    if (!_ready) return "??:??:??";
    DateTime now = _rtc.now();
    char buf[10];
    snprintf(buf, sizeof(buf), "%02d:%02d:%02d", now.hour(), now.minute(), now.second());
    return String(buf);
}

String RtcManager::getDate() {
    if (!_ready) return "00/00/0000";
    DateTime now = _rtc.now();
    char buf[12];
    snprintf(buf, sizeof(buf), "%02d/%02d/%04d", now.day(), now.month(), now.year());
    return String(buf);
}

String RtcManager::getDateTime() {
    if (!_ready) return "0000-00-00T00:00:00";
    DateTime now = _rtc.now();
    char buf[21];
    snprintf(buf, sizeof(buf), "%04d-%02d-%02dT%02d:%02d:%02d",
             now.year(), now.month(), now.day(),
             now.hour(), now.minute(), now.second());
    return String(buf);
}

void RtcManager::syncFromNTP() {
    if (WiFi.status() != WL_CONNECTED) return;

    Serial.println("[RTC] Syncing from NTP...");

    configTime(0, 0, "pool.ntp.org", "time.nist.gov");
    setenv("TZ", "CET-1", 1);
    tzset();

    struct tm timeinfo;
    int attempts = 0;
    while (!getLocalTime(&timeinfo) && attempts < 20) {
        delay(500);
        attempts++;
    }

    if (attempts < 20) {
        Serial.printf("[RTC] NTP UTC: %02d:%02d:%02d\n",
            timeinfo.tm_hour, timeinfo.tm_min, timeinfo.tm_sec);

        DateTime ntpTime(
            timeinfo.tm_year + 1900,
            timeinfo.tm_mon + 1,
            timeinfo.tm_mday,
            timeinfo.tm_hour,
            timeinfo.tm_min,
            timeinfo.tm_sec
        );
        _rtc.adjust(ntpTime);
        Serial.printf("[RTC] Stored: %02d:%02d:%02d\n",
            timeinfo.tm_hour, timeinfo.tm_min, timeinfo.tm_sec);
    } else {
        Serial.println("[RTC] NTP sync failed");
    }
}

bool RtcManager::isReady() {
    return _ready;
}

#include <Arduino.h>
#include <SPI.h>
#include "config.h"
#include "wifi_manager.h"
#include "rfid_reader.h"
#include "display.h"
#include "api_client.h"
#include "feedback.h"
#include "rtc_manager.h"
#include "storage.h"
#include "attendance.h"

WiFiManager wifiManager;
RFIDReader rfidReader;
Display display;
ApiClient apiClient;
Feedback feedback;
RtcManager rtcManager;
Storage storage;
Attendance attendance;

void setup() {
    Serial.begin(115200);
    delay(100);
    Serial.println("\n========================================");
    Serial.println("  PointagePro Terminal v1.0");
    Serial.println("  Sepab Agro - Morneg");
    Serial.println("========================================\n");

    feedback.begin();
    Serial.println("[BOOT] Feedback OK");

    pinMode(PIN_TFT_CS, OUTPUT);
    pinMode(PIN_RC522_SS, OUTPUT);
    pinMode(PIN_SD_CS, OUTPUT);
    digitalWrite(PIN_TFT_CS, HIGH);
    digitalWrite(PIN_RC522_SS, HIGH);
    digitalWrite(PIN_SD_CS, HIGH);
    delay(50);
    Serial.println("[BOOT] CS pins idle HIGH");

    SPI.begin(PIN_SPI_SCK, PIN_SPI_MISO, PIN_SPI_MOSI, -1);
    delay(100);

    display.begin();
    Serial.println("[BOOT] Display OK");

    rtcManager.begin();
    Serial.println("[BOOT] RTC OK, time: " + rtcManager.getTime());

    rfidReader.begin();
    Serial.println("[BOOT] RFID OK");

    storage.begin();
    Serial.println("[BOOT] SD Card " + String(storage.isReady() ? "OK" : "FAIL (not wired yet)"));

    apiClient.begin();
    Serial.println("[BOOT] API Client OK");

    wifiManager.begin();
    Serial.println("[BOOT] WiFi module OK");

    feedback.setLedEffect(LED_CONNECTING);

    if (wifiManager.connect()) {
        Serial.println("[BOOT] WiFi connected");
        rtcManager.syncFromNTP();
    } else {
        Serial.println("[BOOT] WiFi failed, offline mode");
    }

    attendance.begin(&wifiManager, &rfidReader, &display,
                     &apiClient, &feedback, &rtcManager, &storage);

    Serial.println("\n[BOOT] === System Ready ===\n");
}

void loop() {
    wifiManager.loop();
    attendance.loop();
}

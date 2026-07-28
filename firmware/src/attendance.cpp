#include "attendance.h"
#include "wifi_manager.h"
#include "rfid_reader.h"
#include "display.h"
#include "api_client.h"
#include "feedback.h"
#include "rtc_manager.h"
#include "storage.h"
#include "config.h"
#include <WiFi.h>

void Attendance::begin(WiFiManager* wifi, RFIDReader* rfid, Display* display,
                       ApiClient* api, Feedback* feedback, RtcManager* rtc, Storage* storage) {
    _wifi = wifi;
    _rfid = rfid;
    _display = display;
    _api = api;
    _feedback = feedback;
    _rtc = rtc;
    _storage = storage;

    _feedback->setLedEffect(LED_BOOT);
    _display->showBoot();
    delay(2000);

    _feedback->setLedEffect(LED_CONNECTING);
    int pending = _storage->getPendingCount();
    _display->showOffline(_rtc->getTime(), pending);
    _feedback->setLedEffect(LED_READY);
    _booted = true;
}

void Attendance::loop() {
    if (!_booted) return;

    _feedback->loop();
    handleButton();

    bool currentlyOnline = _wifi->isConnected();

    if (currentlyOnline && !_onlineMode) {
        Serial.println("[ATT] Back online, waiting for server...");
        delay(3000);
        _onlineMode = true;
        _api->setOfflineMode(false);
        _rtc->syncFromNTP();
        syncPendingLogs();
    } else if (!currentlyOnline && _onlineMode) {
        Serial.println("[ATT] Gone offline");
        _onlineMode = false;
        _api->setOfflineMode(true);
        int pending = _storage->getPendingCount();
        _display->showOffline(_rtc->getTime(), pending);
        _feedback->setLedEffect(LED_OFFLINE);
    }

    if (!_showingResult && millis() - _lastTimeUpdate > 1000) {
        _lastTimeUpdate = millis();
        _display->updateTime(_rtc->getTime());
    }

    handleHeartbeat();

    if (_showingResult && millis() > _displayReturnTime) {
        _showingResult = false;
        if (_onlineMode) {
            _display->showReady(_rtc->getTime());
            _feedback->setLedEffect(LED_READY);
        } else {
            int pending = _storage->getPendingCount();
            _display->showOffline(_rtc->getTime(), pending);
            _feedback->setLedEffect(LED_OFFLINE);
        }
    }

    if (_rfid->isCardPresent()) {
        String uid = _rfid->readUID();
        if (uid.length() > 0) {
            Serial.println("[ATT] Card: " + uid);
            _feedback->setLedEffect(LED_SCANNING);
            _display->showProcessing();

            unsigned long scanStart = millis();
            while (millis() - scanStart < 300) {
                _feedback->loop();
                delay(10);
            }

            if (_onlineMode) {
                handleOnlineScan(uid);
            } else {
                handleOfflineScan(uid);
            }
        }
    }
}

void Attendance::handleOnlineScan(const String& uid) {
    ScanResponse resp = _api->scan(uid);

    if (resp.success) {
        _display->showSuccess(resp.employeeName, resp.matricule, resp.action, resp.time);
        _feedback->setBuzzer(BUZZ_SHORT);
        _feedback->setLedEffect(LED_SUCCESS);
    } else {
        _display->showError(resp.message);
        _feedback->setBuzzer(BUZZ_ERROR);
        _feedback->setLedEffect(LED_ERROR);
    }

    _showingResult = true;
    _displayReturnTime = millis() + DISPLAY_TIMEOUT_MS;
}

void Attendance::handleOfflineScan(const String& uid) {
    if (!_storage->canLog(uid)) {
        _display->showError("Deja pointe");
        _feedback->setBuzzer(BUZZ_ERROR);
        _feedback->setLedEffect(LED_ERROR);
        _showingResult = true;
        _displayReturnTime = millis() + DISPLAY_TIMEOUT_MS;
        Serial.println("[ATT] Rejected: " + uid + " already has 2 pending scans");
        return;
    }

    _storage->logScan(uid, _rtc->getDateTime());
    _feedback->setBuzzer(BUZZ_SHORT);

    String time = _rtc->getTime();
    _display->showSuccess(uid, "Mode local", "Enregistre", time);
    _feedback->setLedEffect(LED_SUCCESS);

    _showingResult = true;
    _displayReturnTime = millis() + DISPLAY_TIMEOUT_MS;
}

void Attendance::handleButton() {
    bool pressed = (digitalRead(PIN_BUTTON) == LOW);

    if (pressed && !_lastButtonState) {
        unsigned long now = millis();
        if (now - _lastButtonPress > DEBOUNCE_MS) {
            _lastButtonPress = now;

            if (_showingResult) {
                _showingResult = false;
                if (_onlineMode) {
                    _display->showReady(_rtc->getTime());
                } else {
                    int pending = _storage->getPendingCount();
                    _display->showOffline(_rtc->getTime(), pending);
                }
            } else {
                _display->showInfo(_wifi->getLocalIP(), _wifi->getSignalStrength(), _rtc->getDate());
                _showingResult = true;
                _displayReturnTime = millis() + 5000;
            }
        }
    }

    _lastButtonState = pressed;
}

void Attendance::handleHeartbeat() {
    if (_onlineMode && millis() - _lastHeartbeat > HEARTBEAT_INTERVAL_MS) {
        _lastHeartbeat = millis();
        _api->heartbeat();
    }
}

void Attendance::syncPendingLogs() {
    int count = _storage->getPendingCount();
    if (count == 0) {
        _display->showReady(_rtc->getTime());
        _feedback->setLedEffect(LED_READY);
        return;
    }

    Serial.println("[ATT] Syncing " + String(count) + " pending logs");
    _display->showOfflineSync(count);

    int synced = 0;
    int skipped = 0;
    bool connectionLost = false;

    for (int i = 0; i < count; i++) {
        PendingLog log = _storage->getPendingLog(0);
        if (log.synced) break;

        if (WiFi.status() != WL_CONNECTED) {
            Serial.println("[ATT] WiFi lost during sync");
            connectionLost = true;
            break;
        }

        Serial.println("[ATT] Sync: " + log.uid + " @ " + log.timestamp);

        bool serverReached = false;
        for (int retry = 0; retry < 3; retry++) {
            ScanResponse resp = _api->scan(log.uid, log.timestamp);

            if (resp.success) {
                _storage->markSynced(0);
                synced++;
                serverReached = true;
                Serial.println("[ATT] Synced OK: " + log.uid);
                break;
            }

            if (resp.message == "Timeout serveur" || resp.message == "Hors ligne") {
                Serial.println("[ATT] Retry " + String(retry + 1) + "/3...");
                delay(2000);
                continue;
            }

            _storage->markSynced(0);
            skipped++;
            serverReached = true;
            Serial.println("[ATT] Skipped (server: " + resp.message + "): " + log.uid);
            break;
        }

        if (!serverReached) {
            Serial.println("[ATT] Server unreachable, stopping sync");
            break;
        }

        delay(200);
    }

    _storage->clearSynced();

    int processed = synced + skipped;
    Serial.println("[ATT] Sync done, " + String(processed) + "/" + String(count) + " processed (" + String(synced) + " saved, " + String(skipped) + " skipped)");

    if (synced > 0) {
        _display->showSyncSuccess(synced);
        _feedback->setBuzzer(BUZZ_SHORT);
        _feedback->setLedEffect(LED_SUCCESS);
        _showingResult = true;
        _displayReturnTime = millis() + 2000;
    } else if (connectionLost) {
        _display->showError("Serveur indispo");
        _feedback->setBuzzer(BUZZ_ERROR);
        _feedback->setLedEffect(LED_ERROR);
        _showingResult = true;
        _displayReturnTime = millis() + DISPLAY_TIMEOUT_MS;
    } else {
        _display->showReady(_rtc->getTime());
        _feedback->setLedEffect(LED_READY);
    }
}

bool Attendance::isButtonPressed() {
    return digitalRead(PIN_BUTTON) == LOW;
}

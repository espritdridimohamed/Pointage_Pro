#include "storage.h"
#include "config.h"
#include <SD.h>
#include <SPI.h>

void Storage::begin() {
    _prefs.begin("pointagepro", false);

    if (!SD.begin(PIN_SD_CS)) {
        Serial.println("[SD] Card init failed");
        _ready = false;
        return;
    }

    uint8_t cardType = SD.cardType();
    if (cardType == CARD_NONE) {
        Serial.println("[SD] No card found");
        _ready = false;
        return;
    }

    Serial.println("[SD] Card OK, type: " + String(cardType == CARD_MMC ? "MMC" :
                    cardType == CARD_SD ? "SD" : cardType == CARD_SDHC ? "SDHC" : "UNKNOWN"));

    if (!SD.exists(_logFile)) {
        File f = SD.open(_logFile, FILE_WRITE);
        if (f) f.close();
    }

    clearSynced();
    _ready = true;
}

bool Storage::isReady() {
    return _ready;
}

String Storage::_extractUid(const String& line) {
    int uidStart = line.indexOf("\"uid\":\"") + 7;
    int uidEnd = line.indexOf("\"", uidStart);
    if (uidStart < 7 || uidEnd < 0) return "";
    return line.substring(uidStart, uidEnd);
}

String Storage::_extractRef(const String& line) {
    int refStart = line.indexOf("\"ref\":\"") + 7;
    int refEnd = line.indexOf("\"", refStart);
    if (refStart < 7 || refEnd < 0) return "";
    return line.substring(refStart, refEnd);
}

String Storage::nextExternalRef() {
    uint32_t seq = _prefs.getUInt("scanSeq", 0) + 1;
    _prefs.putUInt("scanSeq", seq);

    char buf[16];
    snprintf(buf, sizeof(buf), "%08lu", (unsigned long)seq);
    return String(DEVICE_SERIAL) + "-" + String(buf);
}

bool Storage::canLog(const String& uid) {
    return countPendingForUid(uid) < 2;
}

void Storage::logScan(const String& uid, const String& timestamp, const String& externalRef) {
    if (!_ready) return;

    File f = SD.open(_logFile, FILE_APPEND);
    if (!f) {
        Serial.println("[SD] Failed to open log file");
        return;
    }

    String entry = "{\"uid\":\"" + uid + "\",\"ts\":\"" + timestamp + "\",\"ref\":\"" + externalRef + "\",\"synced\":false}\n";
    f.print(entry);
    f.close();

    Serial.println("[SD] Logged: " + uid + " (" + String(countPendingForUid(uid)) + "/2)");
}

int Storage::getPendingCount() {
    if (!_ready) return 0;

    File f = SD.open(_logFile, FILE_READ);
    if (!f) return 0;

    int count = 0;
    while (f.available()) {
        String line = f.readStringUntil('\n');
        if (line.length() == 0) continue;
        if (line.indexOf("\"synced\":false") >= 0) count++;
    }
    f.close();
    return count;
}

int Storage::countPendingForUid(const String& uid) {
    if (!_ready) return 0;

    File f = SD.open(_logFile, FILE_READ);
    if (!f) return 0;

    int count = 0;
    while (f.available()) {
        String line = f.readStringUntil('\n');
        if (line.length() == 0) continue;
        if (line.indexOf("\"synced\":false") >= 0 && _extractUid(line) == uid) {
            count++;
        }
    }
    f.close();
    return count;
}

PendingLog Storage::getPendingLog(int index) {
    PendingLog log;
    log.synced = true;

    if (!_ready) return log;

    File f = SD.open(_logFile, FILE_READ);
    if (!f) return log;

    int current = 0;
    while (f.available()) {
        String line = f.readStringUntil('\n');
        if (line.length() == 0) continue;
        if (line.indexOf("\"synced\":false") >= 0) {
            if (current == index) {
                log.uid = _extractUid(line);
                log.externalRef = _extractRef(line);

                int tsStart = line.indexOf("\"ts\":\"") + 6;
                int tsEnd = line.indexOf("\"", tsStart);
                if (tsStart >= 6 && tsEnd >= 0) {
                    log.timestamp = line.substring(tsStart, tsEnd);
                }

                log.synced = false;
                break;
            }
            current++;
        }
    }
    f.close();
    return log;
}

void Storage::markSynced(int index) {
    if (!_ready) return;

    File f = SD.open(_logFile, FILE_READ);
    if (!f) return;

    String content = "";
    int current = 0;
    while (f.available()) {
        String line = f.readStringUntil('\n');
        if (line.length() == 0) continue;

        if (line.indexOf("\"synced\":false") >= 0) {
            if (current == index) {
                line.replace("\"synced\":false", "\"synced\":true");
            }
            current++;
        }
        content += line + "\n";
    }
    f.close();

    File fw = SD.open(_logFile, FILE_WRITE);
    if (fw) {
        fw.print(content);
        fw.close();
    }

    Serial.println("[SD] Mark synced index " + String(index));
}

void Storage::clearSynced() {
    if (!_ready) return;

    File f = SD.open(_logFile, FILE_READ);
    if (!f) return;

    String content = "";
    while (f.available()) {
        String line = f.readStringUntil('\n');
        if (line.length() == 0) continue;
        if (line.indexOf("\"synced\":false") >= 0) {
            content += line + "\n";
        }
    }
    f.close();

    File fw = SD.open(_logFile, FILE_WRITE);
    if (fw) {
        fw.print(content);
        fw.close();
    }

    Serial.println("[SD] Cleared synced entries");
}

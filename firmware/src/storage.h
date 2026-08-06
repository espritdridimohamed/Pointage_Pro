#pragma once
#include <Arduino.h>
#include <Preferences.h>

struct PendingLog {
    String uid;
    String timestamp;
    String externalRef;
    bool synced;
};

class Storage {
public:
    void begin();
    bool isReady();
    bool canLog(const String& uid);
    void logScan(const String& uid, const String& timestamp, const String& externalRef);
    int getPendingCount();
    int countPendingForUid(const String& uid);
    PendingLog getPendingLog(int index);
    void markSynced(int index);
    void clearSynced();

    String nextExternalRef();

private:
    bool _ready = false;
    String _logFile = "/pointagepro.log";
    Preferences _prefs;

    String _extractUid(const String& line);
    String _extractRef(const String& line);
};

#pragma once
#include <Arduino.h>

struct PendingLog {
    String uid;
    String timestamp;
    bool synced;
};

class Storage {
public:
    void begin();
    bool isReady();
    bool canLog(const String& uid);
    void logScan(const String& uid, const String& timestamp);
    int getPendingCount();
    int countPendingForUid(const String& uid);
    PendingLog getPendingLog(int index);
    void markSynced(int index);
    void clearSynced();

private:
    bool _ready = false;
    String _logFile = "/pointagepro.log";

    String _extractUid(const String& line);
};

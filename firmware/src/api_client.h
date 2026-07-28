#pragma once
#include <Arduino.h>
#include "config.h"

struct ScanResponse {
    bool success;
    String action;
    String employeeName;
    String matricule;
    String message;
    String time;
};

class ApiClient {
public:
    void begin();
    void setOfflineMode(bool offline);
    bool isOfflineMode();
    ScanResponse scan(const String& rfidUid);
    ScanResponse scan(const String& rfidUid, const String& timestamp);
    bool heartbeat();

private:
    bool _offlineMode = false;
    String _baseUrl = API_BASE_URL;

    String httpPost(const String& endpoint, const String& body);
    ScanResponse scanInternal(const String& rfidUid, const String& timestamp);
};

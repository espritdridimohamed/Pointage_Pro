#pragma once
#include <Arduino.h>

class WiFiManager;
class RFIDReader;
class Display;
class ApiClient;
class Feedback;
class RtcManager;
class Storage;

class Attendance {
public:
    void begin(WiFiManager* wifi, RFIDReader* rfid, Display* display,
               ApiClient* api, Feedback* feedback, RtcManager* rtc, Storage* storage);
    void loop();
    bool isButtonPressed();

private:
    WiFiManager* _wifi = nullptr;
    RFIDReader* _rfid = nullptr;
    Display* _display = nullptr;
    ApiClient* _api = nullptr;
    Feedback* _feedback = nullptr;
    RtcManager* _rtc = nullptr;
    Storage* _storage = nullptr;

    bool _onlineMode = true;
    bool _booted = false;
    unsigned long _lastHeartbeat = 0;
    unsigned long _lastButtonPress = 0;
    unsigned long _displayReturnTime = 0;
    unsigned long _lastTimeUpdate = 0;
    bool _showingResult = false;
    bool _lastButtonState = false;

    void handleOnlineScan(const String& uid);
    void handleOfflineScan(const String& uid);
    void handleButton();
    void handleHeartbeat();
    void syncPendingLogs();
};

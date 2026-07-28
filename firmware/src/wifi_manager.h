#pragma once
#include <Arduino.h>

class WiFiManager {
public:
    void begin();
    bool connect();
    bool isConnected();
    void loop();
    void disconnect();
    String getLocalIP();
    int getSignalStrength();

private:
    bool _wasConnected = false;
    unsigned long _lastReconnectAttempt = 0;
    static const unsigned long RECONNECT_INTERVAL = 30000;
};

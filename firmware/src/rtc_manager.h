#pragma once
#include <Arduino.h>

class RtcManager {
public:
    void begin();
    String getTime();           // "HH:MM:SS"
    String getDate();          // "DD/MM/YYYY"
    String getDateTime();      // "YYYY-MM-DDTHH:MM:SS"
    void syncFromNTP();
    bool isReady();

private:
    bool _ready = false;
};

#pragma once
#include <Arduino.h>
#include <Adafruit_ST7735.h>
#include "config.h"

enum DisplayState {
    DISP_BOOT,
    DISP_READY,
    DISP_PROCESSING,
    DISP_SUCCESS,
    DISP_ERROR,
    DISP_OFFLINE,
    DISP_INFO
};

class Display {
public:
    void begin();
    void showBoot();
    void showReady(const String& time);
    void updateTime(const String& time);
    void showProcessing();
    void showSuccess(const String& name, const String& matricule,
                     const String& action, const String& time);
    void showError(const String& message);
    void showOffline(const String& time = "", int pendingCount = 0);
    void showInfo(const String& ip, int rssi, const String& date);
    void showOfflineSync(int count);
    void showSyncSuccess(int count);
    void setState(DisplayState state);
    DisplayState getState();

private:
    Adafruit_ST7735* _tft = nullptr;
    DisplayState _currentState = DISP_BOOT;

    void drawHeader(const String& title);
    void drawProgressBar(int y, int percent, uint16_t color);
    void clearScreen(uint16_t bg = COLOR_DARK_BG);
    void setTextCentered(int y, const String& text, uint16_t color, uint8_t size = 2);
    String truncateToFit(const String& text, uint8_t size);
};

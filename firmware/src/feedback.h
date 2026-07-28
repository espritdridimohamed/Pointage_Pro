#pragma once
#include <Arduino.h>
#include <FastLED.h>
#include "config.h"

enum LedEffect {
    LED_OFF,
    LED_BOOT,
    LED_READY,
    LED_SCANNING,
    LED_SUCCESS,
    LED_ERROR,
    LED_OFFLINE,
    LED_CONNECTING
};

enum BuzzerPattern {
    BUZZ_NONE,
    BUZZ_SHORT,
    BUZZ_DOUBLE,
    BUZZ_LONG,
    BUZZ_ERROR
};

class Feedback {
public:
    void begin();
    void setLedEffect(LedEffect effect);
    void setBuzzer(BuzzerPattern pattern);
    void loop();

private:
    CRGB _leds[NUM_LEDS];
    LedEffect _currentEffect = LED_OFF;
    unsigned long _effectStartTime = 0;

    void animateBoot();
    void animateReady();
    void animateScanning();
    void animateSuccess();
    void animateError();
    void animateOffline();
    void animateConnecting();
    void allOff();
};

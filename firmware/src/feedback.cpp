#include "feedback.h"
#include "config.h"

static const CRGB COL_TEAL     = CRGB(0x00, 0xE5, 0xCC);
static const CRGB COL_AMBER    = CRGB(0xFF, 0x91, 0x00);
static const CRGB COL_GREEN    = CRGB(0x1A, 0xE8, 0x43);
static const CRGB COL_RED      = CRGB(0xFF, 0x17, 0x44);
static const CRGB COL_ORANGE   = CRGB(0xFF, 0x6D, 0x00);
static const CRGB COL_CYAN     = CRGB(0x00, 0xE5, 0xFF);

void Feedback::begin() {
    FastLED.addLeds<WS2812B, PIN_LED_DIN, GRB>(_leds, NUM_LEDS);
    FastLED.setBrightness(LED_BRIGHTNESS);
    allOff();

    pinMode(PIN_BUZZER, OUTPUT);
    digitalWrite(PIN_BUZZER, LOW);

    pinMode(PIN_BUTTON, INPUT_PULLUP);
}

void Feedback::setLedEffect(LedEffect effect) {
    if (effect != _currentEffect) {
        _currentEffect = effect;
        _effectStartTime = millis();
        allOff();
    }
}

void Feedback::setBuzzer(BuzzerPattern pattern) {
    switch (pattern) {
        case BUZZ_SHORT:
            digitalWrite(PIN_BUZZER, HIGH);
            delay(80);
            digitalWrite(PIN_BUZZER, LOW);
            break;
        case BUZZ_DOUBLE:
            digitalWrite(PIN_BUZZER, HIGH);
            delay(80);
            digitalWrite(PIN_BUZZER, LOW);
            delay(80);
            digitalWrite(PIN_BUZZER, HIGH);
            delay(80);
            digitalWrite(PIN_BUZZER, LOW);
            break;
        case BUZZ_LONG:
            digitalWrite(PIN_BUZZER, HIGH);
            delay(400);
            digitalWrite(PIN_BUZZER, LOW);
            break;
        case BUZZ_ERROR:
            digitalWrite(PIN_BUZZER, HIGH);
            delay(200);
            digitalWrite(PIN_BUZZER, LOW);
            delay(100);
            digitalWrite(PIN_BUZZER, HIGH);
            delay(200);
            digitalWrite(PIN_BUZZER, LOW);
            break;
        default:
            break;
    }
}

void Feedback::loop() {
    switch (_currentEffect) {
        case LED_BOOT:       animateBoot(); break;
        case LED_READY:      animateReady(); break;
        case LED_SCANNING:   animateScanning(); break;
        case LED_SUCCESS:    animateSuccess(); break;
        case LED_ERROR:      animateError(); break;
        case LED_OFFLINE:    animateOffline(); break;
        case LED_CONNECTING: animateConnecting(); break;
        default:             allOff(); break;
    }
}

void Feedback::allOff() {
    fill_solid(_leds, NUM_LEDS, CRGB::Black);
    FastLED.show();
}

void Feedback::animateBoot() {
    unsigned long elapsed = millis() - _effectStartTime;

    if (elapsed >= 2000) {
        _currentEffect = LED_CONNECTING;
        _effectStartTime = millis();
        return;
    }

    int cycleMs = 600;
    int phase = (elapsed % (cycleMs * 2)) / 60;
    bool forward = (elapsed % (cycleMs * 2)) < cycleMs;

    int pos;
    if (forward) {
        pos = phase % NUM_LEDS;
    } else {
        pos = NUM_LEDS - 1 - (phase % NUM_LEDS);
    }

    for (int i = 0; i < NUM_LEDS; i++) {
        int dist = abs(i - pos);
        uint8_t bri = (dist == 0) ? 200 : (dist == 1 ? 80 : (dist == 2 ? 30 : 0));
        _leds[i] = COL_CYAN;
        _leds[i].fadeLightBy(255 - bri);
    }
    FastLED.show();
}

void Feedback::animateConnecting() {
    unsigned long elapsed = millis() - _effectStartTime;

    int pos = (elapsed / 100) % NUM_LEDS;

    for (int i = 0; i < NUM_LEDS; i++) {
        int dist = (i - pos + NUM_LEDS) % NUM_LEDS;
        uint8_t bri;
        switch (dist) {
            case 0: bri = 200; break;
            case 1: bri = 120; break;
            case 2: bri = 50; break;
            default: bri = 0; break;
        }
        _leds[i] = COL_ORANGE;
        _leds[i].fadeLightBy(255 - bri);
    }
    FastLED.show();
}

void Feedback::animateReady() {
    unsigned long elapsed = millis() - _effectStartTime;

    uint8_t brightness = beatsin8(15, 8, 50);
    CRGB col = COL_TEAL;

    for (int i = 0; i < NUM_LEDS; i++) {
        _leds[i] = col;
        _leds[i].fadeLightBy(255 - brightness);
    }
    FastLED.show();
}

void Feedback::animateOffline() {
    unsigned long elapsed = millis() - _effectStartTime;

    uint8_t brightness = beatsin8(12, 5, 40);

    for (int i = 0; i < NUM_LEDS; i++) {
        _leds[i] = COL_AMBER;
        _leds[i].fadeLightBy(255 - brightness);
    }
    FastLED.show();
}

void Feedback::animateScanning() {
    unsigned long elapsed = millis() - _effectStartTime;

    int cycleMs = 240;
    int phase = (elapsed % cycleMs);
    float t = (float)phase / (float)cycleMs;
    float wave = fabs(2.0f * (t - 0.5f));
    int center = (int)(wave * (NUM_LEDS - 1));

    for (int i = 0; i < NUM_LEDS; i++) {
        int dist = abs(i - center);
        uint8_t bri;
        if (dist == 0) bri = 220;
        else if (dist == 1) bri = 100;
        else if (dist == 2) bri = 30;
        else bri = 0;

        _leds[i] = COL_TEAL;
        _leds[i].fadeLightBy(255 - bri);
    }
    FastLED.show();
}

void Feedback::animateSuccess() {
    unsigned long elapsed = millis() - _effectStartTime;

    if (elapsed < 200) {
        fill_solid(_leds, NUM_LEDS, COL_GREEN);
        FastLED.setBrightness(255);
        FastLED.show();
        FastLED.setBrightness(LED_BRIGHTNESS);
    } else if (elapsed < 4500) {
        for (int i = 0; i < NUM_LEDS; i++) {
            _leds[i] = COL_GREEN;
        }
        uint8_t bri = 120 + (uint8_t)(135.0f * (sinf((float)(elapsed - 200) / 400.0f * 3.14159f) * 0.5f + 0.5f));
        for (int i = 0; i < NUM_LEDS; i++) {
            _leds[i].fadeLightBy(255 - bri);
        }
        FastLED.show();
    } else {
        _currentEffect = LED_READY;
        _effectStartTime = millis();
    }
}

void Feedback::animateError() {
    unsigned long elapsed = millis() - _effectStartTime;

    if (elapsed < 100) {
        fill_solid(_leds, NUM_LEDS, COL_RED);
        FastLED.show();
    } else if (elapsed < 200) {
        fill_solid(_leds, NUM_LEDS, CRGB::Black);
        FastLED.show();
    } else if (elapsed < 300) {
        fill_solid(_leds, NUM_LEDS, COL_RED);
        FastLED.show();
    } else if (elapsed < 400) {
        fill_solid(_leds, NUM_LEDS, CRGB::Black);
        FastLED.show();
    } else if (elapsed < 500) {
        fill_solid(_leds, NUM_LEDS, COL_RED);
        FastLED.show();
    } else if (elapsed < 1500) {
        float t = (float)(elapsed - 500) / 1000.0f;
        uint8_t bri = (uint8_t)(200 * (1.0f - t));
        for (int i = 0; i < NUM_LEDS; i++) {
            _leds[i] = COL_RED;
            _leds[i].fadeLightBy(255 - bri);
        }
        FastLED.show();
    } else {
        _currentEffect = LED_READY;
        _effectStartTime = millis();
    }
}

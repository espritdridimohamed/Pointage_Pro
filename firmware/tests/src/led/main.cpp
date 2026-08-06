#include <Arduino.h>
#include <FastLED.h>

#define PIN_LED_DIN 2
#define NUM_LEDS    6

CRGB leds[NUM_LEDS];

void setup() {
    Serial.begin(115200);
    delay(300);
    Serial.println("\n=== WS2812B LED TEST ===");

    FastLED.addLeds<WS2812B, PIN_LED_DIN, GRB>(leds, NUM_LEDS);
    FastLED.setBrightness(128);
    FastLED.clear();
    FastLED.show();

    Serial.print("Testing ");
    Serial.print(NUM_LEDS);
    Serial.println(" LEDs...");
}

void loop() {
    // RED sweep
    for (int i = 0; i < NUM_LEDS; i++) {
        FastLED.clear();
        leds[i] = CRGB::Red;
        FastLED.show();
        Serial.print("RED LED ");
        Serial.println(i + 1);
        delay(500);
    }
    // GREEN sweep
    for (int i = 0; i < NUM_LEDS; i++) {
        FastLED.clear();
        leds[i] = CRGB::Green;
        FastLED.show();
        Serial.print("GREEN LED ");
        Serial.println(i + 1);
        delay(500);
    }
    // BLUE sweep
    for (int i = 0; i < NUM_LEDS; i++) {
        FastLED.clear();
        leds[i] = CRGB::Blue;
        FastLED.show();
        Serial.print("BLUE LED ");
        Serial.println(i + 1);
        delay(500);
    }
    // All on white
    FastLED.clear();
    fill_solid(leds, NUM_LEDS, CRGB::White);
    FastLED.show();
    Serial.println("ALL LEDs WHITE");
    delay(1500);

    FastLED.clear();
    FastLED.show();
    Serial.println("ALL OFF - repeat cycle");
    delay(1000);
}

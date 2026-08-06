#include <Arduino.h>
#include <Wire.h>
#include <RTClib.h>

#define PIN_SDA   21
#define PIN_SCL   22

RTC_DS3231 rtc;

void setup() {
    Serial.begin(115200);
    delay(300);
    Serial.println("\n=== DS3231 RTC TEST ===");

    Wire.begin(PIN_SDA, PIN_SCL);

    // I2C scanner to confirm the module answers on the bus
    Serial.println("Scanning I2C bus...");
    byte found = 0;
    for (byte addr = 1; addr < 127; addr++) {
        Wire.beginTransmission(addr);
        if (Wire.endTransmission() == 0) {
            Serial.print("Device found at 0x");
            Serial.println(addr, HEX);
            found++;
        }
    }
    Serial.print("I2C devices found: ");
    Serial.println(found);

    if (!rtc.begin()) {
        Serial.println("RESULT: FAIL - DS3231 not detected (check SDA=21, SCL=22, VCC, GND)");
        return;
    }

    if (rtc.lostPower()) {
        Serial.println("Note: RTC lost power, setting to compile time");
        rtc.adjust(DateTime(F(__DATE__), F(__TIME__)));
    }

    DateTime now = rtc.now();
    Serial.print("RTC reads: ");
    Serial.print(now.year(), DEC); Serial.print("-");
    Serial.print(now.month(), DEC); Serial.print("-");
    Serial.print(now.day(), DEC); Serial.print(" ");
    Serial.print(now.hour(), DEC); Serial.print(":");
    Serial.print(now.minute(), DEC); Serial.print(":");
    Serial.println(now.second(), DEC);

    // Temperature is a DS3231-only feature, good extra check
    Serial.print("Chip temp: ");
    Serial.print(rtc.getTemperature());
    Serial.println(" C");

    Serial.println("RESULT: OK - DS3231 detected and reading time");
}

void loop() {
    DateTime now = rtc.now();
    Serial.printf("%04d-%02d-%02d %02d:%02d:%02d\n",
                  now.year(), now.month(), now.day(),
                  now.hour(), now.minute(), now.second());
    delay(1000);
}

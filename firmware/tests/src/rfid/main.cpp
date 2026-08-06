#include <Arduino.h>
#include <SPI.h>
#include <MFRC522.h>

#define PIN_SPI_SCK   18
#define PIN_SPI_MOSI  23
#define PIN_SPI_MISO  19
#define PIN_RC522_SS  14

MFRC522 mfrc522(PIN_RC522_SS, -1);

void setup() {
    Serial.begin(115200);
    delay(300);
    Serial.println("\n=== RC522 RFID TEST ===");

    SPI.begin(PIN_SPI_SCK, PIN_SPI_MISO, PIN_SPI_MOSI, -1);
    delay(100);

    mfrc522.PCD_Init();
    delay(200);

    byte v = mfrc522.PCD_ReadRegister(mfrc522.VersionReg);
    Serial.print("RC522 version: 0x");
    Serial.println(v, HEX);

    if (v == 0x00 || v == 0xFF || v == 0x91) {
        Serial.println("RESULT: FAIL - RC522 not responding (check SS=14, power, GND)");
        Serial.println("Version 0x92/0xEE expected");
    } else {
        Serial.println("RESULT: OK - RC522 detected");
    }
    Serial.println("\nNow swipe a badge...");
}

void loop() {
    if (mfrc522.PICC_IsNewCardPresent()) {
        if (mfrc522.PICC_ReadCardSerial()) {
            Serial.print("UID: ");
            for (byte i = 0; i < mfrc522.uid.size; i++) {
                if (mfrc522.uid.uidByte[i] < 0x10) Serial.print("0");
                Serial.print(mfrc522.uid.uidByte[i], HEX);
                if (i < mfrc522.uid.size - 1) Serial.print(":");
            }
            Serial.println();
            mfrc522.PICC_HaltA();
        }
    }
    delay(50);
}

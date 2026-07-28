/*
 * RC522 RFID Reader - Standalone Test
 * Upload this via Arduino IDE to test if your RC522 is wired correctly.
 *
 * Wiring:
 *   SCK  -> GPIO 18
 *   MISO -> GPIO 19
 *   MOSI -> GPIO 23
 *   SS   -> GPIO 5
 *   RST  -> GPIO 2
 *   VCC  -> 3.3V  (NOT 5V!)
 *   GND  -> GND
 *
 * Install in Arduino IDE:
 *   Sketch -> Include Library -> Manage Libraries
 *   Search "MFRC522" by miguelbalboa -> Install
 */

#include <SPI.h>
#include <MFRC522.h>

#define RC522_SS   14
#define RC522_RST  -1

MFRC522 mfrc522(RC522_SS, RC522_RST);

void setup() {
    Serial.begin(115200);
    delay(1000);
    Serial.println();
    Serial.println("================================");
    Serial.println("  RC522 RFID Test - Standalone");
    Serial.println("================================");
    Serial.println();

    SPI.begin(18, 19, 23, RC522_SS);
    delay(200);

    mfrc522.PCD_Init();
    delay(200);

    byte v = mfrc522.PCD_ReadRegister(mfrc522.VersionReg);
    Serial.print("MFRC522 Version: 0x");
    Serial.println(v, HEX);

    if (v == 0x91 || v == 0x92) {
        Serial.println("-> OK! RC522 detected and working.");
    } else if (v == 0x00) {
        Serial.println("-> ERROR: No response. Check wiring and 3.3V power.");
    } else if (v == 0xFF) {
        Serial.println("-> ERROR: Bus garbage. Check SCK/MISO/MOSI/SS/RST wiring.");
    } else {
        Serial.print("-> Unknown version. Could be OK, try scanning a card.");
    }

    Serial.println();
    Serial.println("Place your RFID card on the reader...");
    Serial.println();
}

void loop() {
    if (!mfrc522.PICC_IsNewCardPresent()) return;
    if (!mfrc522.PICC_ReadCardSerial()) return;

    Serial.print("Card UID: ");
    for (byte i = 0; i < mfrc522.uid.size; i++) {
        if (mfrc522.uid.uidByte[i] < 0x10) Serial.print("0");
        Serial.print(mfrc522.uid.uidByte[i], HEX);
        if (i < mfrc522.uid.size - 1) Serial.print(":");
    }
    Serial.println();

    Serial.print("UID (no separators): ");
    for (byte i = 0; i < mfrc522.uid.size; i++) {
        if (mfrc522.uid.uidByte[i] < 0x10) Serial.print("0");
        Serial.print(mfrc522.uid.uidByte[i], HEX);
    }
    Serial.println();

    Serial.print("Type: ");
    mfrc522.PICC_DumpDetailsToSerial(&(mfrc522.uid));

    mfrc522.PICC_HaltA();
    mfrc522.PCD_StopCrypto1();

    Serial.println();
    Serial.println("Place another card...");
    delay(1000);
}

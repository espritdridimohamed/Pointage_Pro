#include <Arduino.h>
#include <SPI.h>
#include <SD.h>

#define PIN_SPI_SCK   18
#define PIN_SPI_MOSI  23
#define PIN_SPI_MISO  19
#define PIN_SD_CS     26

void setup() {
    Serial.begin(115200);
    delay(300);
    Serial.println("\n=== SD CARD TEST ===");

    SPI.begin(PIN_SPI_SCK, PIN_SPI_MISO, PIN_SPI_MOSI, -1);
    delay(100);

    if (!SD.begin(PIN_SD_CS)) {
        Serial.println("RESULT: FAIL - SD init failed");
        Serial.println("Check: CS=26, SCK=18, MOSI=23, MISO=19, VCC=3.3V, GND");
        return;
    }

    uint8_t cardType = SD.cardType();
    if (cardType == CARD_NONE) {
        Serial.println("RESULT: FAIL - no card detected");
        return;
    }

    Serial.print("Card type: ");
    Serial.println(cardType == CARD_MMC ? "MMC" :
                   cardType == CARD_SD ? "SD" : cardType == CARD_SDHC ? "SDHC" : "UNKNOWN");

    Serial.print("Size: ");
    Serial.print(SD.cardSize() / 1024 / 1024);
    Serial.println(" MB");

    // Write test
    File f = SD.open("/test.txt", FILE_WRITE);
    if (!f) {
        Serial.println("RESULT: FAIL - cannot open file for writing");
        return;
    }
    f.println("PointagePro SD test OK");
    f.close();
    Serial.println("Wrote test.txt");

    // Read test
    f = SD.open("/test.txt", FILE_READ);
    if (!f) {
        Serial.println("RESULT: FAIL - cannot open file for reading");
        return;
    }
    Serial.print("Read back: ");
    while (f.available()) {
        Serial.write(f.read());
    }
    f.close();
    SD.remove("/test.txt");
    Serial.println("RESULT: OK - SD read/write works");
}

void loop() {
    delay(5000);
}

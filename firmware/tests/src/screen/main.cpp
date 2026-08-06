#include <Arduino.h>
#include <SPI.h>
#include <Adafruit_ST7735.h>
#include <Adafruit_GFX.h>

#define PIN_SPI_SCK   18
#define PIN_SPI_MOSI  23
#define PIN_SPI_MISO  19
#define PIN_TFT_CS    4
#define PIN_TFT_DC    27
#define PIN_TFT_RST   13
#define TFT_WIDTH     128
#define TFT_HEIGHT    160

Adafruit_ST7735 tft = Adafruit_ST7735(PIN_TFT_CS, PIN_TFT_DC, PIN_TFT_RST);

void setup() {
    Serial.begin(115200);
    delay(300);
    Serial.println("\n=== ST7735 SCREEN TEST ===");

    SPI.begin(PIN_SPI_SCK, PIN_SPI_MISO, PIN_SPI_MOSI, -1);
    delay(100);

    tft.initR(INITR_BLACKTAB);
    tft.setRotation(0);
    tft.fillScreen(ST77XX_BLACK);

    Serial.println("If screen is working you should see color bars + text:");
    Serial.println("  Red / Green / Blue top bars");
    Serial.println("  White text 'SCREEN OK'");

    // Color bars across the top
    tft.fillRect(0, 0, TFT_WIDTH, 30, ST77XX_RED);
    tft.fillRect(0, 30, TFT_WIDTH, 30, ST77XX_GREEN);
    tft.fillRect(0, 60, TFT_WIDTH, 30, ST77XX_BLUE);
    delay(500);

    // White text below
    tft.fillRect(0, 90, TFT_WIDTH, 40, ST77XX_BLACK);
    tft.setTextColor(ST77XX_WHITE);
    tft.setTextSize(2);
    tft.setCursor(10, 100);
    tft.print("SCREEN");
    tft.setCursor(10, 120);
    tft.print("OK");
}

void loop() {
    delay(1000);
    Serial.println("RESULT: If you see RED/GREEN/BLUE bars + white SCREEN OK -> WORKING");
    delay(5000);
}

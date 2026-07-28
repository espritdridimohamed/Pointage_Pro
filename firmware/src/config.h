#pragma once
#include <Arduino.h>

// ─── WiFi ───
#define WIFI_SSID          "iPhone_Hamma"
#define WIFI_PASSWORD      "269414144"
#define WIFI_TIMEOUT_MS    10000

// ─── Backend API ───
#define API_BASE_URL       "http://172.20.10.8:8080/api/v1"
#define API_SCAN_ENDPOINT  "/esp32/scan"
#define API_HEARTBEAT_EP   "/esp32/heartbeat"
#define API_KEY            "pointagepro-esp32-device-key-2026"
#define API_TIMEOUT_MS     5000
#define HEARTBEAT_INTERVAL_MS 60000

// ─── SPI Bus (shared: RC522, ST7735, SD Card) ───
#define PIN_SPI_SCK        18
#define PIN_SPI_MOSI       23
#define PIN_SPI_MISO       19

// ─── RC522 RFID ───
#define PIN_RC522_SS       14

// ─── ST7735 TFT Display (1.8") ───
#define PIN_TFT_CS         4
#define PIN_TFT_DC         27
#define PIN_TFT_RST        13
#define TFT_WIDTH          128
#define TFT_HEIGHT         160
#define TFT_ROTATION       0

// ─── DS3231 RTC (I2C) ───
#define PIN_SDA            21
#define PIN_SCL            22

// ─── WS2812B LEDs (6 LEDs) ───
#define PIN_LED_DIN        2
#define NUM_LEDS           6
#define LED_BRIGHTNESS     200

// ─── Buzzer ───
#define PIN_BUZZER         25

// ─── Button ───
#define PIN_BUTTON         26

// ─── SD Card ───
#define PIN_SD_CS          15

// ─── Timing ───
#define DEBOUNCE_MS        200
#define DISPLAY_TIMEOUT_MS 5000

// ─── Colors (RGB565 for ST7735) ───
#define COLOR_BLACK        0x0000
#define COLOR_WHITE        0xFFFF
#define COLOR_RED          0xF800
#define COLOR_GREEN        0x07E0
#define COLOR_BLUE         0x001F
#define COLOR_YELLOW       0xFFE0
#define COLOR_ORANGE       0xFD20
#define COLOR_CYAN         0x07FF
#define COLOR_DARK_BG      0x10A2
#define COLOR_HEADER_BG    0x1823

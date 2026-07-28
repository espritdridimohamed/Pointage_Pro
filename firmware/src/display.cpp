#include "display.h"
#include "config.h"

void Display::begin() {
    _tft = new Adafruit_ST7735(PIN_TFT_CS, PIN_TFT_DC, PIN_TFT_RST);
    _tft->initR(INITR_BLACKTAB);
    _tft->setRotation(TFT_ROTATION);
    _tft->fillScreen(COLOR_DARK_BG);
    _tft->setTextWrap(false);
}

void Display::clearScreen(uint16_t bg) {
    _tft->fillScreen(bg);
}

void Display::drawHeader(const String& title) {
    _tft->fillRect(0, 0, TFT_WIDTH, 32, COLOR_HEADER_BG);
    _tft->setTextSize(1);
    _tft->setTextColor(COLOR_CYAN);
    _tft->setCursor(10, 6);
    _tft->print("POINTAGEPRO");
    _tft->setTextColor(COLOR_WHITE);
    _tft->setCursor(10, 18);
    _tft->print(title);
}

void Display::setTextCentered(int y, const String& text, uint16_t color, uint8_t size) {
    _tft->setTextSize(size);
    _tft->setTextColor(color);
    int16_t textWidth = text.length() * 6 * size;
    int x = (TFT_WIDTH - textWidth) / 2;
    if (x < 0) x = 2;
    _tft->setCursor(x, y);
    _tft->print(text);
}

String Display::truncateToFit(const String& text, uint8_t size) {
    int maxChars = TFT_WIDTH / (6 * size);
    if ((int)text.length() <= maxChars) return text;
    return text.substring(0, maxChars - 1) + ".";
}

void Display::showBoot() {
    _currentState = DISP_BOOT;
    clearScreen(COLOR_DARK_BG);

    _tft->setTextSize(2);
    _tft->setTextColor(COLOR_CYAN);
    _tft->setCursor(14, 40);
    _tft->print("POINTAGE");
    _tft->setTextColor(COLOR_WHITE);
    _tft->setCursor(50, 60);
    _tft->print("PRO");

    setTextCentered(90, "Systeme de Pointage", COLOR_WHITE, 1);
    setTextCentered(120, "Initialisation...", 0x8410, 1);
}

void Display::showReady(const String& time) {
    _currentState = DISP_READY;
    clearScreen(COLOR_DARK_BG);
    drawHeader("En attente");

    setTextCentered(48, "Scannez", COLOR_WHITE, 2);
    setTextCentered(72, "votre", COLOR_WHITE, 2);
    setTextCentered(96, "badge", COLOR_WHITE, 2);

    setTextCentered(128, time, COLOR_CYAN, 2);
}

void Display::updateTime(const String& time) {
    if (_currentState != DISP_READY && _currentState != DISP_OFFLINE) return;
    if (_currentState == DISP_READY) {
        _tft->fillRect(0, 125, TFT_WIDTH, 20, COLOR_DARK_BG);
        setTextCentered(128, time, COLOR_CYAN, 2);
    } else {
        _tft->fillRect(0, 137, TFT_WIDTH, 20, COLOR_DARK_BG);
        setTextCentered(140, time, COLOR_CYAN, 2);
    }
}

void Display::showProcessing() {
    _currentState = DISP_PROCESSING;
    _tft->fillRoundRect(10, 40, TFT_WIDTH - 20, 100, 8, COLOR_HEADER_BG);
    setTextCentered(55, "Traitement", COLOR_WHITE, 2);
    setTextCentered(75, "en cours", COLOR_YELLOW, 2);

    for (int i = 0; i < 3; i++) {
        _tft->fillCircle(40 + i * 24, 110, 4, COLOR_CYAN);
        delay(200);
    }
}

void Display::showSuccess(const String& name, const String& matricule,
                           const String& action, const String& time) {
    _currentState = DISP_SUCCESS;
    clearScreen(COLOR_DARK_BG);

    String displayAction = action;
    if (action == "CHECK_IN") displayAction = "Arrivee";
    else if (action == "CHECK_OUT") displayAction = "Depart";

    uint16_t actionColor = (displayAction == "Arrivee") ? COLOR_GREEN : COLOR_ORANGE;

    drawHeader(displayAction);

    _tft->fillRoundRect(10, 38, TFT_WIDTH - 20, 115, 8, COLOR_HEADER_BG);

    String displayName = truncateToFit(name, 2);
    setTextCentered(44, displayName, COLOR_WHITE, 2);

    _tft->setTextSize(1);
    _tft->setTextColor(0x8410);
    String displayMatricule = truncateToFit(matricule, 1);
    setTextCentered(66, displayMatricule, 0x8410, 1);

    _tft->drawRoundRect(20, 80, TFT_WIDTH - 40, 26, 6, actionColor);
    setTextCentered(85, displayAction, actionColor, 2);

    setTextCentered(115, time, COLOR_WHITE, 2);

    setTextCentered(145, "Enregistre", COLOR_GREEN, 1);
}

void Display::showError(const String& message) {
    _currentState = DISP_ERROR;
    clearScreen(COLOR_DARK_BG);
    drawHeader("Erreur");

    _tft->fillRoundRect(10, 45, TFT_WIDTH - 20, 80, 8, 0x4000);

    setTextCentered(55, "Erreur", COLOR_RED, 2);

    String displayMsg = truncateToFit(message, 1);
    setTextCentered(80, displayMsg, COLOR_WHITE, 1);
}

void Display::showOffline(const String& time, int pendingCount) {
    _currentState = DISP_OFFLINE;
    clearScreen(COLOR_DARK_BG);
    drawHeader("Hors ligne");

    setTextCentered(44, "Mode local actif", COLOR_YELLOW, 1);

    _tft->setTextSize(1);
    _tft->setTextColor(0x8410);
    _tft->setCursor(10, 64);
    _tft->print("Les scanns seront");
    _tft->setCursor(10, 76);
    _tft->print("synchronises au");
    _tft->setCursor(10, 88);
    _tft->print("retour du reseau.");

    if (pendingCount > 0) {
        setTextCentered(106, String(pendingCount) + " scan" + (pendingCount > 1 ? "s" : "") + " en attente", COLOR_ORANGE, 1);
    }

    if (time.length() > 0) {
        setTextCentered(140, time, COLOR_CYAN, 2);
    }
}

void Display::showInfo(const String& ip, int rssi, const String& date) {
    _currentState = DISP_INFO;
    clearScreen(COLOR_DARK_BG);
    drawHeader("Systeme");

    _tft->setTextSize(1);
    int y = 42;

    _tft->setTextColor(0x8410);
    _tft->setCursor(10, y); _tft->print("IP: " + ip); y += 14;

    String signal = "RSSI: " + String(rssi) + " dBm";
    uint16_t sigColor = (rssi > -60) ? COLOR_GREEN : (rssi > -80) ? COLOR_YELLOW : COLOR_RED;
    _tft->setTextColor(sigColor);
    _tft->setCursor(10, y); _tft->print(signal); y += 14;

    _tft->setTextColor(0x8410);
    _tft->setCursor(10, y); _tft->print("WiFi: " + String(WIFI_SSID)); y += 14;
    _tft->setCursor(10, y); _tft->print("Date: " + date); y += 20;

    _tft->setTextColor(COLOR_CYAN);
    _tft->setCursor(10, y); _tft->print("PointagePro v1.0");
    y += 14;
    _tft->setTextColor(0x8410);
    _tft->setCursor(10, y); _tft->print("Sepab Agro - Morneg");
}

void Display::showOfflineSync(int count) {
    _currentState = DISP_OFFLINE;
    clearScreen(COLOR_DARK_BG);
    drawHeader("Synchronisation");

    setTextCentered(50, String(count) + " scanns", COLOR_YELLOW, 2);
    setTextCentered(70, "en attente", COLOR_YELLOW, 2);

    drawProgressBar(100, 0, COLOR_BLUE);
}

void Display::showSyncSuccess(int count) {
    _currentState = DISP_READY;
    clearScreen(COLOR_DARK_BG);
    drawHeader("Synchronisation");

    setTextCentered(55, String(count) + " scanns", COLOR_GREEN, 2);
    setTextCentered(75, "synchronises", COLOR_GREEN, 2);
    setTextCentered(105, "avec succes", COLOR_GREEN, 1);
}

void Display::drawProgressBar(int y, int percent, uint16_t color) {
    int barWidth = TFT_WIDTH - 40;
    _tft->drawRoundRect(20, y, barWidth, 12, 4, 0x8410);
    int filled = (barWidth - 4) * percent / 100;
    if (filled > 0) {
        _tft->fillRoundRect(22, y + 2, filled, 8, 3, color);
    }
}

void Display::setState(DisplayState state) { _currentState = state; }
DisplayState Display::getState() { return _currentState; }

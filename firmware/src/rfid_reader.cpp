#include "rfid_reader.h"
#include "config.h"
#include <SPI.h>

void RFIDReader::begin() {
    _mfrc522 = new MFRC522(PIN_RC522_SS, -1);
    delay(100);
    _mfrc522->PCD_Init();
    delay(200);

    byte v = _mfrc522->PCD_ReadRegister(_mfrc522->VersionReg);
    Serial.print("[RFID] RC522 version: 0x");
    Serial.println(v, HEX);

    if (v == 0x00 || v == 0xFF) {
        Serial.println("[RFID] WARNING: Bad version. Retrying PCD_Init...");
        _mfrc522->PCD_Reset();
        delay(500);
        _mfrc522->PCD_Init();
        delay(200);

        v = _mfrc522->PCD_ReadRegister(_mfrc522->VersionReg);
        Serial.print("[RFID] Retry version: 0x");
        Serial.println(v, HEX);
    }
}

bool RFIDReader::isCardPresent() {
    SPI.end();
    SPI.begin(PIN_SPI_SCK, PIN_SPI_MISO, PIN_SPI_MOSI, -1);
    delay(5);

    if (!_mfrc522->PICC_IsNewCardPresent()) return false;
    if (!_mfrc522->PICC_ReadCardSerial()) return false;
    return true;
}

String RFIDReader::readUID() {
    String uid = "";
    for (byte i = 0; i < _mfrc522->uid.size; i++) {
        if (_mfrc522->uid.uidByte[i] < 0x10) uid += "0";
        uid += String(_mfrc522->uid.uidByte[i], HEX);
        if (i < _mfrc522->uid.size - 1) uid += ":";
    }
    uid.toUpperCase();

    _mfrc522->PICC_HaltA();
    _mfrc522->PCD_StopCrypto1();
    delay(100);

    return uid;
}

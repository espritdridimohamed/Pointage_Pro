#pragma once
#include <Arduino.h>
#include <MFRC522.h>

class RFIDReader {
public:
    void begin();
    bool isCardPresent();
    String readUID();

private:
    MFRC522* _mfrc522 = nullptr;
};

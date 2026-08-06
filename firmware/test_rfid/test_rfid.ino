#include <SPI.h>

#include <Adafruit_GFX.h>
#include <Adafruit_ST7735.h>

#include <MFRC522v2.h>
#include <MFRC522DriverSPI.h>
#include <MFRC522DriverPinSimple.h>


// =====================
// TFT PINS
// =====================
#define TFT_CS   4
#define TFT_DC   27
#define TFT_RST  13


// =====================
// RFID PINS
// =====================
#define RFID_SS   14
#define RFID_RST  32


// =====================
// OBJECTS
// =====================

Adafruit_ST7735 tft(
  TFT_CS,
  TFT_DC,
  TFT_RST
);


MFRC522DriverPinSimple ss_pin(RFID_SS);

MFRC522DriverSPI driver{
  ss_pin
};

MFRC522 rfid{
  driver
};


// =====================
// SETUP
// =====================

void setup()
{

  Serial.begin(115200);
  delay(1000);


  Serial.println("====================");
  Serial.println(" RFID + TFT TEST");
  Serial.println("====================");


  // Disable both devices first

  pinMode(TFT_CS, OUTPUT);
  digitalWrite(TFT_CS, HIGH);


  pinMode(RFID_SS, OUTPUT);
  digitalWrite(RFID_SS, HIGH);



  // Start SPI

  SPI.begin(
    18,
    19,
    23
  );


  Serial.println("[SPI] OK");



  // =====================
  // RFID FIRST
  // =====================

  Serial.println("[RFID]");


  pinMode(RFID_RST, OUTPUT);

  digitalWrite(RFID_RST, LOW);
  delay(50);

  digitalWrite(RFID_RST, HIGH);
  delay(50);


  digitalWrite(TFT_CS, HIGH);


  rfid.PCD_Init();

  delay(100);


  Serial.println("RFID READY");



  // =====================
  // TFT SECOND
  // =====================

  Serial.println("[TFT]");


  digitalWrite(RFID_SS, HIGH);


  tft.initR(INITR_BLACKTAB);

  delay(100);


  tft.setRotation(1);

  tft.fillScreen(ST77XX_BLACK);


  tft.setTextColor(ST77XX_GREEN);
  tft.setTextSize(2);

  tft.setCursor(10,20);

  tft.println("READY");


  digitalWrite(TFT_CS, HIGH);


  Serial.println("TFT OK");


  Serial.println("====================");
  Serial.println("SCAN CARD");
  Serial.println("====================");

}


// =====================
// LOOP
// =====================

void loop()
{

  // keep TFT inactive
  digitalWrite(TFT_CS, HIGH);


  if(!rfid.PICC_IsNewCardPresent())
  {
    return;
  }


  if(!rfid.PICC_ReadCardSerial())
  {
    return;
  }


  Serial.print("UID: ");


  for(byte i = 0; i < rfid.uid.size; i++)
  {

    if(rfid.uid.uidByte[i] < 0x10)
      Serial.print("0");


    Serial.print(
      rfid.uid.uidByte[i],
      HEX
    );

    Serial.print(" ");
  }


  Serial.println();


  digitalWrite(RFID_SS, HIGH);


  tft.fillScreen(ST77XX_BLACK);

  tft.setCursor(10,20);

  tft.setTextColor(ST77XX_YELLOW);

  tft.setTextSize(2);

  tft.println("CARD OK");


  rfid.PICC_HaltA();

  delay(1000);

}
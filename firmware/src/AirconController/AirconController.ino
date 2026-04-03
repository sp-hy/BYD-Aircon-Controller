// Arduino IDE: install "NimBLE-Arduino" by h2zero (Library Manager). Board:
// ESP32C6 Dev Module.
//
// Phone/car "Bluetooth" settings may show "Connect" for BLE — that is not the
// same as this app's GATT connection. Use the Aircon Controller foreground
// service to connect for notifications.
#include <Arduino.h>
#include <NimBLEDevice.h>

namespace {
constexpr int BUTTON_PIN = 9;
constexpr unsigned long DEBOUNCE_MS = 180;

constexpr char DEVICE_NAME[] = "BYD-Aircon";
constexpr char SERVICE_UUID[] = "0f1d2a40-2f5f-4a4d-b3c1-91f7b799f0a1";
constexpr char COMMAND_CHAR_UUID[] = "8388fdd2-cd4e-4f6d-a32f-03c2f0bc62a5";
constexpr char INFO_CHAR_UUID[] = "e90d8e4e-cf9b-4dcc-859b-f8c1db9bea60";

NimBLECharacteristic *commandChar = nullptr;
bool lastButtonState = HIGH;
unsigned long lastEdgeMs = 0;
} // namespace

void setupBle() {
  NimBLEDevice::init(DEVICE_NAME);
  NimBLEServer *server = NimBLEDevice::createServer();
  NimBLEService *service = server->createService(SERVICE_UUID);

  commandChar = service->createCharacteristic(
      COMMAND_CHAR_UUID, NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::NOTIFY);
  uint8_t initialCommand = 0x00;
  commandChar->setValue(&initialCommand, 1);

  NimBLECharacteristic *infoChar =
      service->createCharacteristic(INFO_CHAR_UUID, NIMBLE_PROPERTY::READ);
  infoChar->setValue("proto-v1");

  service->start();

  NimBLEAdvertising *advertising = NimBLEDevice::getAdvertising();
  advertising->setName(DEVICE_NAME);
  advertising->addServiceUUID(SERVICE_UUID);
  advertising->start();
}

void setup() {
  pinMode(BUTTON_PIN, INPUT_PULLUP);
  Serial.begin(115200);
  delay(300);
  Serial.println("BYD Aircon BLE button boot");
  setupBle();
}

void loop() {
  bool buttonState = digitalRead(BUTTON_PIN);
  unsigned long now = millis();

  bool isFallingEdge = (lastButtonState == HIGH && buttonState == LOW);
  bool debounced = (now - lastEdgeMs) >= DEBOUNCE_MS;

  if (isFallingEdge && debounced && commandChar != nullptr) {
    uint8_t toggleCommand = 0x01; // 0x01 = toggle climate
    commandChar->setValue(&toggleCommand, 1);
    commandChar->notify();
    lastEdgeMs = now;
    Serial.println("Sent BLE command: toggle");
  }

  lastButtonState = buttonState;
  delay(8);
}

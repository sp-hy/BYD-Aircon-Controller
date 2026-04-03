// Arduino IDE: install "NimBLE-Arduino" by h2zero (Library Manager).
// Board: Seeed XIAO ESP32C6 (ESP32C6 Dev Module or Seeed board package).
//
// Phone/car "Bluetooth" settings may show "Connect" for BLE — that is not the
// same as this app's GATT connection. Use the Aircon Controller foreground
// service to connect for notifications.
#include <Arduino.h>
#include <NimBLEDevice.h>

namespace {
// Seeed XIAO ESP32-C6: use Arduino pin names (D0–D10). Tie each pin to GND to press (INPUT_PULLUP).
// Notify byte = pin number: D0→0x00, D1→0x01, D2→0x02, D3→0x03 (one byte per press).
struct InputPin {
  int arduinoPin;
  uint8_t notifyByte;
  const char *label;
  bool lastState;
  unsigned long lastEdgeMs;
  int lastLoggedLevel;
};

InputPin kInputs[] = {
    {D0, 0x00, "D0", HIGH, 0, -1},
    {D1, 0x01, "D1", HIGH, 0, -1},
    {D2, 0x02, "D2", HIGH, 0, -1},
    {D3, 0x03, "D3", HIGH, 0, -1},
};

constexpr size_t kNumInputs = sizeof(kInputs) / sizeof(kInputs[0]);
constexpr unsigned long DEBOUNCE_MS = 180;

constexpr char DEVICE_NAME[] = "BYD-Aircon";
constexpr char SERVICE_UUID[] = "0f1d2a40-2f5f-4a4d-b3c1-91f7b799f0a1";
constexpr char COMMAND_CHAR_UUID[] = "8388fdd2-cd4e-4f6d-a32f-03c2f0bc62a5";
constexpr char INFO_CHAR_UUID[] = "e90d8e4e-cf9b-4dcc-859b-f8c1db9bea60";

NimBLECharacteristic *commandChar = nullptr;

/** After a central disconnects, advertising must be restarted or reconnects will fail. */
class AirconServerCallbacks : public NimBLEServerCallbacks {
  void onDisconnect(NimBLEServer *pServer, NimBLEConnInfo &connInfo,
                      int reason) override {
    (void)pServer;
    (void)connInfo;
    (void)reason;
    NimBLEDevice::startAdvertising();
  }
};

static AirconServerCallbacks serverCallbacks;
} // namespace

void setupBle() {
  NimBLEDevice::init(DEVICE_NAME);
  NimBLEServer *server = NimBLEDevice::createServer();
  server->setCallbacks(&serverCallbacks);
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
  Serial.begin(115200);
  delay(300);
  Serial.println("BYD Aircon BLE button boot");
  for (size_t i = 0; i < kNumInputs; i++) {
    pinMode(kInputs[i].arduinoPin, INPUT_PULLUP);
    Serial.printf("%s: pin %d, INPUT_PULLUP — initial level=%d\n", kInputs[i].label,
                   kInputs[i].arduinoPin, digitalRead(kInputs[i].arduinoPin));
  }
  setupBle();
}

void loop() {
  unsigned long now = millis();

  for (size_t i = 0; i < kNumInputs; i++) {
    InputPin &in = kInputs[i];
    bool st = digitalRead(in.arduinoPin);

    if ((int)st != in.lastLoggedLevel) {
      in.lastLoggedLevel = st;
      Serial.printf("%s (pin %d) level=%d\n", in.label, in.arduinoPin, st);
    }

    bool falling = (in.lastState == HIGH && st == LOW);
    bool debounced = (now - in.lastEdgeMs) >= DEBOUNCE_MS;
    if (falling && debounced && commandChar != nullptr) {
      commandChar->setValue(&in.notifyByte, 1);
      commandChar->notify();
      in.lastEdgeMs = now;
      Serial.printf("Sent BLE command: %s byte=0x%02x\n", in.label, in.notifyByte);
    }
    in.lastState = st;
  }

  delay(8);
}

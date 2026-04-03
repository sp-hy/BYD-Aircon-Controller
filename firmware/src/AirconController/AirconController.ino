// Arduino IDE: install "NimBLE-Arduino" by h2zero (Library Manager).
// Board: Seeed XIAO ESP32C6 (ESP32C6 Dev Module or Seeed board package).
//
// Phone/car "Bluetooth" settings may show "Connect" for BLE — that is not the
// same as this app's GATT connection. Use the Aircon Controller foreground
// service to connect for notifications.
#include <Arduino.h>
#include <NimBLEDevice.h>

namespace {
// Seeed XIAO ESP32-C6: tie each pin to GND to press (INPUT_PULLUP). D0 omitted — often busy (boot/USB/etc.).
// Notify byte = Arduino D number: D1→0x01 … D4→0x04 (one byte per press).
struct InputPin {
  int arduinoPin;
  uint8_t notifyByte;
  const char *label;
  bool lastState;
  unsigned long lastEdgeMs;
  int lastLoggedLevel;
};

InputPin kInputs[] = {
    {D1, 0x01, "D1", HIGH, 0, -1},
    {D2, 0x02, "D2", HIGH, 0, -1},
    {D3, 0x03, "D3", HIGH, 0, -1},
    {D4, 0x04, "D4", HIGH, 0, -1},
};

constexpr size_t kNumInputs = sizeof(kInputs) / sizeof(kInputs[0]);
constexpr unsigned long DEBOUNCE_MS = 180;

// B103 potentiometer as temperature dial: ends → 3V3 and GND, wiper → analog input.
// Use A0 on XIAO ESP32-C6 — D6 is UART TX / not valid for this chip's ADC path and will panic on read.
// ESP32 ADC is noisy: multi-sample + EMA. Optional hardware: 100nF from wiper to GND, short leads.
constexpr int POT_PIN = A0;
constexpr unsigned long POT_LOG_INTERVAL_MS = 100;
constexpr int POT_AVG_SAMPLES = 8;
// EMA: filtered = (filtered * (EMA_DEN-1) + sample) / EMA_DEN — higher = smoother, slower to move.
constexpr int POT_EMA_DEN = 4;

// Pot calibration (based on your observed raw range).
// These map the analog dial to the car scale [17..33] where:
// 17 = Low, 33 = High.
constexpr int POT_CAL_MIN_RAW = 90;
constexpr int POT_CAL_MAX_RAW = 3780;

// Car display bounds (inclusive).
constexpr int POT_CAR_MIN = 17;
constexpr int POT_CAR_MAX = 33;

constexpr char DEVICE_NAME[] = "BYD-Aircon";
constexpr char SERVICE_UUID[] = "0f1d2a40-2f5f-4a4d-b3c1-91f7b799f0a1";
constexpr char COMMAND_CHAR_UUID[] = "8388fdd2-cd4e-4f6d-a32f-03c2f0bc62a5";
constexpr char INFO_CHAR_UUID[] = "e90d8e4e-cf9b-4dcc-859b-f8c1db9bea60";

NimBLECharacteristic *commandChar = nullptr;
unsigned long lastPotLogMs = 0;
int potFiltered = -1;

/** Average many ADC reads + exponential smoothing so the "floor" and dial position don't jump. */
int readPotStable() {
  uint32_t sum = 0;
  for (int i = 0; i < POT_AVG_SAMPLES; i++) {
    sum += analogRead(POT_PIN);
    delayMicroseconds(80);
  }
  int avg = (int)(sum / (unsigned)POT_AVG_SAMPLES);
  if (potFiltered < 0) {
    potFiltered = avg;
  } else {
    potFiltered = (potFiltered * (POT_EMA_DEN - 1) + avg) / POT_EMA_DEN;
  }
  return potFiltered;
}

int mapPotRawToCarScale(int raw) {
  // Clamp to calibrated range.
  if (raw <= POT_CAL_MIN_RAW) return POT_CAR_MIN;
  if (raw >= POT_CAL_MAX_RAW) return POT_CAR_MAX;

  // Linear map: [POT_CAL_MIN_RAW..POT_CAL_MAX_RAW] -> [POT_CAR_MIN..POT_CAR_MAX]
  // Use integer math with rounding.
  const int inSpan = POT_CAL_MAX_RAW - POT_CAL_MIN_RAW;
  const int outSpan = POT_CAR_MAX - POT_CAR_MIN;
  const int x = raw - POT_CAL_MIN_RAW;
  return POT_CAR_MIN + (outSpan * x + inSpan / 2) / inSpan;
}

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

  analogReadResolution(12);
  analogSetPinAttenuation(POT_PIN, ADC_11db);
  Serial.printf(
      "POT (B103) on A0: %d-sample avg + EMA — raw/mV every %lu ms\n",
      POT_AVG_SAMPLES, POT_LOG_INTERVAL_MS);

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

  if (now - lastPotLogMs >= POT_LOG_INTERVAL_MS) {
    lastPotLogMs = now;
    int raw = readPotStable();
    // Approximate mV from filtered raw (matches ~0–3300 mV at 11 dB on 12-bit).
    int mv = (raw * 3300) / 4095;
    int mapped = mapPotRawToCarScale(raw);
    Serial.printf(
        "POT A0 raw=%d ~mV=%d mapped=%d (range %d..%d -> %d..%d)\n",
        raw,
        mv,
        mapped,
        POT_CAL_MIN_RAW,
        POT_CAL_MAX_RAW,
        POT_CAR_MIN,
        POT_CAR_MAX
    );
  }

  delay(8);
}

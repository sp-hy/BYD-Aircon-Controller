# Aircon Controller

Proof-of-concept Android app that runs **on the car’s DiLink head unit** and drives climate control through BYD’s local `bydauto` APIs. It binds the OEM climate device (`android.hardware.bydauto.ac.BYDAutoAcDevice`) in-process and exposes the same HVAC actions as the native climate UI.

## Requirements

- DiLink 5 head unit (Android 11+, minSdk 30)
- USB debugging enabled
- `com.byd.data.collect` installed on the unit

## How it works

The `bydauto` classes are not on the boot classpath. They ship inside the system package `com.byd.data.collect`. Binding also requires `BYDAUTO_AC_*` grants and a hidden-API exemption for `com.ts.*` / `dalvik.system`. The OEM binary is never bundled or written to disk.

### Permission channel

`AdbPermissionManager` connects to `adbd` on `127.0.0.1:5555` using [dadb](https://github.com/mobile-dev-inc/dadb) and a persistent key pair. After the user accepts USB debugging, the app runs as shell and:

- `pm grant`s `WRITE_SECURE_SETTINGS`, `READ_LOGS`, and the `BYDAUTO_AC_*` / setting / bodywork permissions in the manifest
- (opt-in) writes `hidden_api_policy=1` and `hidden_api_blacklist_exemptions='Lcom/ts/,Ldalvik/system/'`

Hidden-API enforcement is captured at process fork. After consent the process restarts. The exemption is global and resets on reboot; it is re-applied on the next launch if consent is stored.

### SDK load

`Dilink5SdkInjector` locates `com.byd.data.collect`, reads `classes*.dex` into memory (`DexPathList.makeInMemoryDexElements`), and appends those elements to this app’s `PathClassLoader`. `Class.forName("android.hardware.bydauto.ac.BYDAutoAcDevice")` then resolves to the live OEM implementation.

### Climate client

`BydAcController` calls `getInstance(Context)` reflectively. `BydPermissionContext` wraps the `Context` and treats `android.permission.BYDAUTO_*` as granted for `check*` / `enforce*` (SET is typically signature-level). Overloads are tried until the first success; fan remembers the working setter.

| Control | API |
|---------|-----|
| On / off | `start` / `stop`, `setAcStartState` |
| Driver / passenger temp | `setAcTemperature` (zone 1 driver, zone 2 passenger, °C). Getter spelling: `getTemprature`. Passenger set enables dual-zone if the firmware exposes a separate/dual temp-control mode. |
| Fan | `set(1000, 0x1DE0000C, level)` (1–7). Named `setAcWindLevel` argument order is firmware-dependent and is not used first. Manual mode is set only when currently Auto. |
| Auto | `setAcControlMode` |
| Recirc / fresh | `setAcCycleMode` (toggle) |
| Front demist | `setAcDefrostState` / `setAcWindMode` (windscreen) |
| Rear window and mirrors | Rear window and wing-mirror heaters (one OEM switch), `setAcDefrostState` rear area |
| Air only | `setAcVentilationState`; fallback `setAcCompressorMode` |
| Max cool | `setAcMaxCoolingState` |

Temp zones: 1 driver set, 2 passenger set, 4 outside.

## First run

1. Enable USB debugging on the head unit.
2. Sideload and launch the app.
3. Accept **Allow USB debugging**.
4. Allow the hidden-API exemption; the app restarts.
5. HVAC controls become active once status shows a bound AC snapshot.

## Source

| Path | Role |
|------|------|
| `adb/AdbPermissionManager.kt` | Local ADB grants and hidden-API exemption |
| `byd/Dilink5SdkInjector.kt` | In-memory load of `com.byd.data.collect` |
| `byd/BydPermissionContext.kt` | Client-side `BYDAUTO_*` permission wrapper |
| `byd/BydAcController.kt` | Reflective AC get/set |
| `MainActivity.kt` | Setup UI and climate controls |
| `service/AirconForegroundService.kt` | Optional ESP32 BLE listener |

## Notes

- Firmware signatures drift; **Dump AC methods** lists live members on the unit.
- The hidden-API exemption resets on reboot and is re-applied after prior consent.
- Injection requires `com.byd.data.collect`.
- Seat heat/vent is not mapped (`BYDAutoAcDevice` does not cover it).

---

## Credits

DiLink 5 permission and SDK loading follow **BYD Trip Stats** ([angoikon/byd-trip-stats](https://github.com/angoikon/byd-trip-stats), [DILINK5.md](https://github.com/angoikon/byd-trip-stats/blob/main/docs/DILINK5.md)) by Angelos Oikonomou and contributors, specifically `AdbPermissionManager` and `Dilink5SdkInjector`.

`dadb`: [mobile-dev-inc/dadb](https://github.com/mobile-dev-inc/dadb).

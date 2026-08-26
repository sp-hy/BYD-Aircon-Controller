# BYD Aircon Controller

Proof-of-concept Android app that runs **on the car’s DiLink head unit** and drives climate control through BYD’s local `bydauto` APIs — not the cloud BYD app.

This replaced an earlier approach that logged into BYD’s remote HTTP/MQTT APIs with email, password, and control PIN. That path is gone. The head unit already has the OEM climate stack; the problem is getting a sideloaded app permission to call it.

## What this proves

On DiLink 5 (Android 11+, e.g. Sealion 7) the OEM classes (`android.hardware.bydauto.*`) are **not** on the boot classpath. They live inside the system app `com.byd.data.collect`. A normal third-party APK therefore:

1. Cannot resolve `BYDAutoAcDevice` unless those classes are loaded into its own classloader.
2. Cannot call hidden `com.ts.lib.caradapter` members the SDK uses, because hidden-API enforcement blocks them (`NoSuchMethodError` / bind failure).
3. Cannot `pm grant` itself `BYDAUTO_AC_*` (and related) permissions without an elevated shell.

This app is a small UI on top of the same workaround [BYD Trip Stats](https://github.com/angoikon/byd-trip-stats) uses for telemetry: a **user-approved local ADB channel**, then live classloader injection of the already-installed OEM APK. No `bydauto` binary is bundled, extracted, or committed.

If climate ON/OFF and setpoint actually move the car’s HVAC, the PoC is validated.

## How it works

```
┌─ Aircon Controller (sideloaded) ─────────────────────────────────┐
│  MainActivity buttons  →  BydAcController (reflection)           │
│         │                                                        │
│         │  Class.forName("…BYDAutoAcDevice").getInstance(ctx)    │
│         ▼                                                        │
│  Dilink5SdkInjector  ── reads ──►  com.byd.data.collect APK      │
│         │                         (already on the head unit)     │
│         └── appends its dex to this app’s PathClassLoader        │
│                                                                  │
│  AdbPermissionManager  ── dadb ──►  127.0.0.1:5555 (adbd)        │
│         │                                                        │
│         ├── pm grant … BYDAUTO_AC_COMMON / GET / SET             │
│         └── settings put global hidden_api_blacklist_exemptions  │
│             'Lcom/ts/,Ldalvik/system/'   (opt-in; resets on boot)│
└──────────────────────────────────────────────────────────────────┘
```

### 1. Local ADB self-authorization

`AdbPermissionManager` talks to `adbd` on `127.0.0.1:5555` with a persistent key pair (via [dadb](https://github.com/mobile-dev-inc/dadb)). First run shows the car’s **Allow USB debugging** dialog. After that, the app can run shell as the `shell` uid and:

- `pm grant` `WRITE_SECURE_SETTINGS`, `READ_LOGS`, and the `BYDAUTO_AC_*` / setting / bodywork permissions declared in the manifest
- Optionally write the **hidden-API exemption** (global device setting) so `com.ts.*` and `dalvik.system` members are callable

Hidden-API enforcement is latched at process fork, so after the user consents the app **restarts itself**. The exemption is global and **clears on reboot**; on the next launch (if consent was stored) it is re-applied only when missing.

### 2. OEM SDK injection

`Dilink5SdkInjector` does not ship BYD’s SDK. It locates `com.byd.data.collect`, reads `classes*.dex` from that APK **in memory**, and appends those dex elements to this app’s classloader. `BYDAutoAcDevice` then resolves to the real implementation.

Injection needs the `Ldalvik/system/` exemption (`BaseDexClassLoader.pathList` / `DexPathList.makeInMemoryDexElements` are hidden).

### 3. Climate calls

`BydAcController` binds `android.hardware.bydauto.ac.BYDAutoAcDevice` reflectively (no compile-time OEM stubs). SET methods are tried in several overloads because DiLink 3 vs 5 signatures drift:

| Action        | Methods tried (first success wins) |
|---------------|------------------------------------|
| On            | `start(0/1)`, `setAcStartState(1, …)` |
| Off           | `stop(0/1)`, `setAcStartState(0, …)` |
| Driver temp   | `setAcTemperature(zone, °C, source, …)` / `setTemprature` typo |
| Fan           | `setAcWindLevel` then base `set(1000, 0x1DE0000C, level)` |
| Auto / recirc | `setAcControlMode`, `setAcCycleMode` |

`BydPermissionContext` wraps `Context` and treats `BYDAUTO_*` as granted for `check*` / `enforce*` — SET is often signature-level and cannot be `pm grant`ed. That only covers **client-side** checks; any server-side IPC policy still applies.

Getters use the OEM spelling `getTemprature(zone)`: zone 1 driver set, 2 passenger set, 4 outside/ambient.

## First run on the car

1. Enable **USB debugging** (Developer options) on the head unit.
2. Sideload the APK (`./gradlew :app:assembleDebug`).
3. Open the app. Accept **Allow USB debugging**.
4. Allow **vehicle data access** (hidden-API exemption). The app restarts.
5. Status should show a bound AC snapshot. Use the climate buttons.

If ADB is not up, **Authorize ADB / grant APIs** tries to open Developer settings.

## UI (PoC)

- Climate ON / OFF  
- Temp ±, Fan ±, Auto, Recirc, Max cool  
- Refresh status  
- **Dump AC methods** — lists live `bydauto` members on this firmware (use this if a SET call returns `no such method` / `invalid value`)  
- Optional ESP32 BLE listener (temperature setpoints go to the local AC API; seat heat/vent is **not** mapped yet)

## Limitations

- Proof of concept, not a product. Firmware and DiLink generation matter.
- Seat heating / ventilation is a different OEM surface than `BYDAutoAcDevice`; BLE seat buttons currently only log.
- Hidden-API exemption resets on reboot (re-applied after prior consent).
- Requires USB debugging and a one-time Allow on the car screen.
- `com.byd.data.collect` must be present. If it is not, injection cannot work.
- SET success on DiLink 3 dumps is not a guarantee on every DiLink 5 firmware; dump methods and logcat (`BydAcController`, `Dilink5SdkInjector`, `AdbPermissionManager`) are the debug path.

## Build

```bash
./gradlew :app:assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

Min SDK 30 (Android 11), aimed at DiLink 5 head units.

## Layout

| Path | Role |
|------|------|
| `adb/AdbPermissionManager.kt` | Local dadb grants + hidden-API exemption |
| `byd/Dilink5SdkInjector.kt` | Load `com.byd.data.collect` into this process |
| `byd/BydPermissionContext.kt` | Client-side `BYDAUTO_*` check/enforce wrapper |
| `byd/BydAcController.kt` | Reflective AC get/set |
| `MainActivity.kt` | Setup + climate buttons |
| `service/AirconForegroundService.kt` | Optional ESP32 BLE → local AC |

---

## Credits

The DiLink 5 permission and SDK-loading approach is adapted from **BYD Trip Stats** by [Angelos Oikonomou](https://github.com/angoikon) and contributors:

- **App / source:** [https://github.com/angoikon/byd-trip-stats](https://github.com/angoikon/byd-trip-stats)
- **DiLink 5 notes:** [docs/DILINK5.md](https://github.com/angoikon/byd-trip-stats/blob/main/docs/DILINK5.md)

In particular this PoC follows trip-stats’:

- `AdbPermissionManager` (local `dadb` to `127.0.0.1:5555`, `pm grant`, hidden-API exemption + process restart)
- `Dilink5SdkInjector` (in-memory dex from `com.byd.data.collect`, no OEM binary in git)

A copy of that project was used as the in-tree reference (`byd-trip-stats-main/`). BYD Trip Stats is licensed under [Business Source License 1.1](https://github.com/angoikon/byd-trip-stats/blob/main/LICENSE.md); this app is a separate climate PoC, not a fork of trip-stats’ product.

Climate SET method names and encoding quirks also draw on public DiLink `BYDAutoAcDevice` dumps (notably zone indices and the `Temprature` typo).

`dadb` is [mobile-dev-inc/dadb](https://github.com/mobile-dev-inc/dadb).

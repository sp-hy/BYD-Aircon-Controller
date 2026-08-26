# Pro unlock codes — BYD Trip Stats Pro

Short, car-typable Pro unlock codes, verified offline on-device.

```
code = crockfordBase32( HMAC-SHA256(secret, "<tier>|<lowercased vehicleId>") )[:10]
     → 10 chars, A–Z + 2–9 (no I/L/O/U), case-insensitive   e.g.  7NPXRA8B3V
```

The code is derived from the buyer's **Vehicle ID**, so it only unlocks the car it
was minted for — sharing it is useless. Symmetric: the same secret mints and
verifies.

- Generator (this dir): [`gen_code.py`](gen_code.py), secret in `secret.key` (gitignored)
- Verifier (**private module**): `ProLicenseHooks.SECRET_B64` + `expectedCode()` in
  `private-telemetry/src/main/java/com/byd/tripstats/runtime/ProLicenseHooks.kt`
- Reflective bridge (app): [`ProLicenseBridge.kt`](../../app/src/main/java/com/byd/tripstats/data/entitlement/ProLicenseBridge.kt)
- Input handling + gate (app): [`LicenseCode.kt`](../../app/src/main/java/com/byd/tripstats/data/entitlement/LicenseCode.kt),
  [`EntitlementManager.kt`](../../app/src/main/java/com/byd/tripstats/data/entitlement/EntitlementManager.kt)

## Issue a code

The buyer copies their **Vehicle ID** from Settings → BYD Trip Stats Pro and sends it.
Then:

```bash
./gen_code.py {bydID}        # -> 7NPXSA4B3V
```

Send back the 10-char code; they enter it under **Settings → BYD Trip Stats Pro →
Enter unlock code**. (`secret.key` must hold the same secret as
`ProLicenseHooks.SECRET_B64`.)

## ⚠️ Security

- **`secret.key` must NEVER be committed** (it's gitignored). Anyone with it can mint
  codes for any vehicle.
- **Before selling, regenerate your own secret** and put it in both places:

  ```bash
  openssl rand -base64 32 | tee secret.key
  # paste the same value into ProLicenseHooks.SECRET_B64 (private-telemetry module)
  ```

- The secret ships in the **private** `private-telemetry` module's bytecode, so a
  reverse-engineer could extract it and mint free codes. That's accepted: it's ≈ the
  effort of just patching the gate out, and the app needs that private module to read
  telemetry at all. Per-vehicle codes still fully stop casual code-sharing — the real
  threat. Honor-system friction, not hard DRM (the app is source-available, BUSL-1.1).

## Why a short symmetric code (not the old RSA key)

A public-key signature is ≥ 86 chars even at its smallest — unusable to type on a head
unit. A short offline code requires a symmetric secret. The previous RSA scheme (only
the public key shipped) was more tamper-proof but produced ~445-char keys. The
per-vehicle code trades that for a 10-char code while still defeating sharing.

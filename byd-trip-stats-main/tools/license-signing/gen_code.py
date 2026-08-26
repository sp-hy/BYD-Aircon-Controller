#!/usr/bin/env python3
"""Generate a BYD Trip Stats Pro unlock code for a vehicle.

The code is a short, car-typable string derived from the buyer's Vehicle ID:

    code = crockfordBase32( HMAC-SHA256(secret, "<tier>|<lowercased vehicleId>") )[:10]

Because it's derived from the vehicle id, a code only unlocks the car it was minted
for. Verified offline on-device by the private `ProLicenseHooks` (same secret, same
algorithm). The buyer copies their Vehicle ID from Settings -> BYD Trip Stats Pro and
sends it; you run this and send back the 10-char code.

Usage:
    ./gen_code.py ${bydID}
    ./gen_code.py ${bydID} --tier pro

The secret is read from `secret.key` (gitignored) and MUST match
`ProLicenseHooks.SECRET_B64` embedded in the private telemetry module.
"""
import argparse
import base64
import hashlib
import hmac
import pathlib
import re
import sys

HERE = pathlib.Path(__file__).resolve().parent
SECRET_FILE = HERE / "secret.key"

ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"  # Crockford base32 (no I, L, O, U)
CODE_LENGTH = 10


def crockford_base32(data: bytes, length: int) -> str:
    """MSB-first 5-bit grouping; must stay byte-for-byte identical to ProLicenseHooks.kt."""
    out = []
    buffer = 0
    bits = 0
    i = 0
    while len(out) < length:
        if bits < 5:
            buffer = (buffer << 8) | data[i]
            bits += 8
            i += 1
        index = (buffer >> (bits - 5)) & 0x1F
        bits -= 5
        out.append(ALPHABET[index])
    return "".join(out)


def main() -> None:
    ap = argparse.ArgumentParser(description="Generate a Pro unlock code for a vehicle.")
    ap.add_argument("vehicle_id", help="the buyer's Vehicle ID (from their Pro card)")
    ap.add_argument("--tier", default="pro", help='entitlement tier (default "pro")')
    args = ap.parse_args()

    if not SECRET_FILE.exists():
        sys.exit(f"secret not found: {SECRET_FILE}\n"
                 "Create it with: openssl rand -base64 32 > secret.key  "
                 "(and embed the same value in ProLicenseHooks.SECRET_B64)")

    secret = base64.b64decode(SECRET_FILE.read_text().strip())
    # Canonicalise to [A-Za-z0-9] then lowercase — must stay byte-for-byte in sync with
    # ProLicenseHooks.expectedCode(). Dropping all non-alphanumerics (not just trimming)
    # makes the id robust to NUL/control padding on hardware serials (e.g. the DiLink-5
    # T-Box serial) that would otherwise differ between device and generator.
    vehicle_id = re.sub(r'[^A-Za-z0-9]', '', args.vehicle_id).lower()
    if not vehicle_id:
        sys.exit("vehicle id is empty")

    mac = hmac.new(secret, f"{args.tier}|{vehicle_id}".encode("utf-8"), hashlib.sha256).digest()
    print(crockford_base32(mac, CODE_LENGTH))


if __name__ == "__main__":
    main()

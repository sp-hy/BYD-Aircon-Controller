package com.byd.tripstats.data.entitlement

/**
 * Pure helpers for Pro unlock codes — no Android, so unit-testable. The actual
 * code derivation (HMAC over the vehicle id) lives in the private telemetry module
 * (`ProLicenseHooks`); this file only canonicalises user input and compares.
 *
 * Codes use a Crockford-style base32 alphabet (digits + A–Z minus I, L, O, U), so
 * input is forgiving: case-insensitive, separators ignored, and the usual look-alikes
 * folded (O→0, I/L→1).
 */
object LicenseCode {

    private val ALLOWED = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toSet()

    /** Canonicalise a typed code: uppercase, fold look-alikes, drop anything else. */
    fun normalizeInput(raw: String): String =
        raw.uppercase()
            .map { c ->
                when (c) {
                    'O' -> '0'
                    'I', 'L' -> '1'
                    else -> c
                }
            }
            .filter { it in ALLOWED }
            .joinToString("")

    /** True if [entered] canonicalises to the [expected] canonical code. */
    fun matches(entered: String, expected: String?): Boolean {
        if (expected.isNullOrEmpty()) return false
        return normalizeInput(entered) == expected.uppercase()
    }
}

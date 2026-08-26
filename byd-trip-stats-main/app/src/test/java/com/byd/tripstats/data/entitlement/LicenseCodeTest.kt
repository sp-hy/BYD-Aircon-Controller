package com.byd.tripstats.data.entitlement

import org.junit.Assert.*
import org.junit.Test

/**
 * Pure tests for unlock-code input canonicalisation. The HMAC derivation is the
 * private module's job (cross-validated in ProLicenseHooksTest), so these need no
 * private module — they run in CI, which builds the app without it.
 */
class LicenseCodeTest {

    @Test fun `matches is case-insensitive and ignores separators`() {
        assertTrue(LicenseCode.matches("7npxra8b3v", "7NPXRA8B3V"))
        assertTrue(LicenseCode.matches("7NPX-RA8B-3V", "7NPXRA8B3V"))
        assertTrue(LicenseCode.matches("  7NPX RA8B 3V  ", "7NPXRA8B3V"))
    }

    @Test fun `look-alike characters are folded`() {
        // O→0, I→1, L→1 — a user typing the letters still matches a code with digits.
        assertEquals("0111", LicenseCode.normalizeInput("OILl"))
        assertTrue(LicenseCode.matches("O11O", "0110"))
    }

    @Test fun `a wrong code does not match`() {
        assertFalse(LicenseCode.matches("N63Q5YBGB0", "7NPXRA8B3V"))
    }

    @Test fun `null or empty expected never matches`() {
        assertFalse(LicenseCode.matches("7NPXRA8B3V", null))
        assertFalse(LicenseCode.matches("7NPXRA8B3V", ""))
    }

    @Test fun `normalizeInput drops characters outside the alphabet`() {
        // U is not in the Crockford alphabet (and not a fold target) → dropped.
        assertEquals("ABC123", LicenseCode.normalizeInput("a.b/c 1_2-3"))
        assertEquals("123", LicenseCode.normalizeInput("U1U2U3"))
    }
}

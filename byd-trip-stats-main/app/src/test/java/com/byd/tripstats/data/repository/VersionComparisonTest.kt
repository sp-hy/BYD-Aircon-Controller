package com.byd.tripstats.data.repository

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for the version comparison logic in UpdateRepository.
 *
 * isNewerVersion() is private — we test it via a thin wrapper that mirrors
 * the exact implementation. If the implementation changes, the wrapper must
 * be kept in sync.
 */
class VersionComparisonTest {

    /** Mirrors UpdateRepository.isNewerVersion() exactly. */
    private fun isNewerVersion(latest: String, current: String): Boolean {
        fun parse(v: String) = v.split(".").map { it.toIntOrNull() ?: 0 }
        val l = parse(latest); val c = parse(current)
        for (i in 0 until maxOf(l.size, c.size)) {
            val diff = (l.getOrElse(i) { 0 }) - (c.getOrElse(i) { 0 })
            if (diff != 0) return diff > 0
        }
        return false
    }

    // ── Newer ─────────────────────────────────────────────────────────────────

    @Test fun `patch bump is newer`() {
        assertTrue(isNewerVersion("1.2.2", "1.2.1"))
    }

    @Test fun `minor bump is newer`() {
        assertTrue(isNewerVersion("1.3.0", "1.2.1"))
    }

    @Test fun `major bump is newer`() {
        assertTrue(isNewerVersion("2.0.0", "1.9.9"))
    }

    @Test fun `large patch gap is newer`() {
        assertTrue(isNewerVersion("1.2.10", "1.2.9"))
    }

    @Test fun `minor bump overrides higher patch`() {
        // 1.3.0 > 1.2.99
        assertTrue(isNewerVersion("1.3.0", "1.2.99"))
    }

    // ── Same version ─────────────────────────────────────────────────────────

    @Test fun `identical versions return false`() {
        assertFalse(isNewerVersion("1.2.1", "1.2.1"))
    }

    @Test fun `identical major minor return false`() {
        assertFalse(isNewerVersion("1.0.0", "1.0.0"))
    }

    // ── Older ────────────────────────────────────────────────────────────────

    @Test fun `older patch returns false`() {
        assertFalse(isNewerVersion("1.2.0", "1.2.1"))
    }

    @Test fun `older minor returns false`() {
        assertFalse(isNewerVersion("1.1.9", "1.2.0"))
    }

    @Test fun `older major returns false`() {
        assertFalse(isNewerVersion("1.9.9", "2.0.0"))
    }

    // ── Edge cases ────────────────────────────────────────────────────────────

    @Test fun `two-segment versions compare correctly`() {
        assertTrue(isNewerVersion("2.0", "1.9"))
        assertFalse(isNewerVersion("1.0", "1.0"))
    }

    @Test fun `v prefix stripped from tag_name`() {
        // GitHub returns "v1.3.0" — caller strips the 'v' before passing here
        // This test confirms our expectation of the stripped form
        assertTrue(isNewerVersion("1.3.0", "1.2.1"))
    }

    @Test fun `different length versions handled correctly`() {
        // "1.3" vs "1.2.1" — missing third segment treated as 0
        assertTrue(isNewerVersion("1.3", "1.2.1"))   // 1.3.0 > 1.2.1
        assertFalse(isNewerVersion("1.2", "1.2.1"))  // 1.2.0 < 1.2.1
    }
}
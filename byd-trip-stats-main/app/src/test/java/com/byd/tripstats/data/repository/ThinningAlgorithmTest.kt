package com.byd.tripstats.data.repository

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for the tiered data-point thinning algorithm used in
 * TripRepository.thinOldDataPoints().
 *
 * The algorithm is extracted here as a pure function so it can be tested
 * without a Room database. If the implementation in TripRepository changes,
 * keep this mirror in sync.
 */
class ThinningAlgorithmTest {

    /**
     * Mirrors TripRepository.thinOldDataPoints() keep-interval selection.
     * Returns the interval in ms for a trip of [ageMs] milliseconds.
     */
    private fun keepIntervalMs(ageMs: Long): Long {
        val day30Ms = 30L * 24L * 3_600_000L
        val day90Ms = 90L * 24L * 3_600_000L
        return when {
            ageMs < day30Ms -> 2_000L
            ageMs < day90Ms -> 10_000L
            else            -> 15_000L
        }
    }

    /**
     * Applies the thinning algorithm to a list of timestamps.
     * Returns the indices that would be DELETED (mirrors doDelete logic).
     * First and last are always kept.
     */
    private fun thin(timestamps: List<Long>, keepIntervalMs: Long): List<Int> {
        if (timestamps.size < 3) return emptyList()
        val toDelete = mutableListOf<Int>()
        var lastKept = timestamps.first()
        for (i in 1 until timestamps.lastIndex) {
            if (timestamps[i] - lastKept >= keepIntervalMs) {
                lastKept = timestamps[i]
            } else {
                toDelete.add(i)
            }
        }
        return toDelete
    }

    // ── Tier selection ────────────────────────────────────────────────────────

    @Test fun `7-to-30 day trips use 2 second interval`() {
        val day8 = 8L * 24L * 3_600_000L
        assertEquals(2_000L, keepIntervalMs(day8))
    }

    @Test fun `30-to-90 day trips use 10 second interval`() {
        val day45 = 45L * 24L * 3_600_000L
        assertEquals(10_000L, keepIntervalMs(day45))
    }

    @Test fun `over-90 day trips use 15 second interval`() {
        val day100 = 100L * 24L * 3_600_000L
        assertEquals(15_000L, keepIntervalMs(day100))
    }

    @Test fun `boundary at exactly 30 days uses 10 second interval`() {
        val day30 = 30L * 24L * 3_600_000L
        assertEquals(10_000L, keepIntervalMs(day30))
    }

    @Test fun `boundary at exactly 90 days uses 15 second interval`() {
        val day90 = 90L * 24L * 3_600_000L
        assertEquals(15_000L, keepIntervalMs(day90))
    }

    // ── First and last always kept ─────────────────────────────────────────

    @Test fun `first point is always kept`() {
        val timestamps = listOf(0L, 500L, 1000L, 1500L, 2000L)
        val deleted = thin(timestamps, 2_000L)
        assertFalse("First point must never be deleted", deleted.contains(0))
    }

    @Test fun `last point is always kept`() {
        val timestamps = listOf(0L, 500L, 1000L, 1500L, 2000L)
        val deleted = thin(timestamps, 2_000L)
        assertFalse("Last point must never be deleted", deleted.contains(timestamps.lastIndex))
    }

    // ── Reduction counts ──────────────────────────────────────────────────────

    @Test fun `1-second interval data halved by 2-second keep interval`() {
        // 120 points at 1s intervals — keep every 2nd → ~60 kept
        val timestamps = (0 until 120).map { it * 1_000L }
        val deleted = thin(timestamps, 2_000L)
        val kept = timestamps.size - deleted.size
        // Should keep roughly half — within 10% tolerance
        assertTrue("Expected ~60 kept, got $kept", kept in 55..65)
    }

    @Test fun `1-second data reduced to ~8 percent by 15-second interval`() {
        // 300 points at 1s → keep every 15th → ~20 kept
        val timestamps = (0 until 300).map { it * 1_000L }
        val deleted = thin(timestamps, 15_000L)
        val kept = timestamps.size - deleted.size
        assertTrue("Expected ~20 kept, got $kept", kept in 18..22)
    }

    @Test fun `no points deleted when already sparse enough`() {
        // Points already 30s apart — 15s interval should not delete any interior points
        val timestamps = (0 until 10).map { it * 30_000L }
        val deleted = thin(timestamps, 15_000L)
        assertEquals("No points should be deleted when already sparse", 0, deleted.size)
    }

    @Test fun `fewer than 3 points are never thinned`() {
        assertEquals(emptyList<Int>(), thin(listOf(0L), 2_000L))
        assertEquals(emptyList<Int>(), thin(listOf(0L, 1_000L), 2_000L))
        assertEquals(emptyList<Int>(), thin(emptyList(), 2_000L))
    }

    @Test fun `exactly 3 points — only middle is candidate`() {
        // Middle is 500ms after first — less than 2s keep → should be deleted
        val timestamps = listOf(0L, 500L, 4_000L)
        val deleted = thin(timestamps, 2_000L)
        assertEquals(listOf(1), deleted)
    }

    @Test fun `exactly 3 points where middle is far enough — nothing deleted`() {
        val timestamps = listOf(0L, 2_500L, 5_000L)
        val deleted = thin(timestamps, 2_000L)
        assertEquals(emptyList<Int>(), deleted)
    }

    // ── Continuity at tier boundaries ─────────────────────────────────────────

    @Test fun `thinning is deterministic — same input yields same output`() {
        val timestamps = (0 until 200).map { it * 1_000L }
        val deleted1 = thin(timestamps, 10_000L)
        val deleted2 = thin(timestamps, 10_000L)
        assertEquals(deleted1, deleted2)
    }

    @Test fun `tighter interval never deletes more than looser interval`() {
        val timestamps = (0 until 100).map { it * 1_000L }
        val deletedTight = thin(timestamps, 2_000L).size
        val deletedLoose = thin(timestamps, 15_000L).size
        assertTrue(
            "Looser interval should delete more: tight=$deletedTight loose=$deletedLoose",
            deletedLoose >= deletedTight
        )
    }
}
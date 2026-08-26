package com.byd.tripstats.service

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for the cell-imbalance alert state machine (debounce, SoC guard,
 * one-shot-with-hysteresis). Pure JVM — no Android context required.
 *
 * Spreads are kept clear of the exact threshold (0.050) and the hysteresis
 * boundary (0.045) so floating-point noise can never flip a comparison:
 *   0.02 / 0.04 → below;  0.047 → inside the hysteresis band;  0.06 → breach.
 */
class CellImbalanceEvaluatorTest {

    private val threshold = 0.05     // 50 mV
    private val vMin = 3.300
    private val streak = CellImbalanceEvaluator.REQUIRED_STREAK

    /** One tick at the given spread (V) and SoC; in-band and enabled by default. */
    private fun CellImbalanceEvaluator.tick(
        spread: Double,
        soc: Double = 50.0,
        enabled: Boolean = true,
    ): Boolean = evaluate(
        enabled = enabled,
        vMax = vMin + spread,
        vMin = vMin,
        soc = soc,
        thresholdV = threshold,
    )

    private fun CellImbalanceEvaluator.countFires(n: Int, spread: Double, soc: Double = 50.0): Int {
        var fires = 0
        repeat(n) { if (tick(spread, soc)) fires++ }
        return fires
    }

    // ── Below threshold ─────────────────────────────────────────────────────────

    @Test fun `a spread below the threshold never fires`() {
        val e = CellImbalanceEvaluator()
        assertEquals(0, e.countFires(30, spread = 0.02))
        assertEquals(0, e.breachStreak)
        assertFalse(e.alerted)
    }

    // ── Sustained-breach debounce ─────────────────────────────────────────────────

    @Test fun `a single breaching tick does not fire`() {
        val e = CellImbalanceEvaluator()
        assertFalse(e.tick(0.06))
        assertEquals(1, e.breachStreak)
    }

    @Test fun `fires only after the required number of consecutive breaches`() {
        val e = CellImbalanceEvaluator()
        repeat(streak - 1) { assertFalse(e.tick(0.06)) }
        assertTrue(e.tick(0.06))          // the streak-th breach fires
        assertTrue(e.alerted)
    }

    @Test fun `fires exactly once per breach episode`() {
        val e = CellImbalanceEvaluator()
        assertEquals(1, e.countFires(streak, spread = 0.06))   // one fire on the way up
        assertEquals(0, e.countFires(20, spread = 0.06))       // never again while still high
    }

    @Test fun `a sub-threshold reading breaks an in-progress streak`() {
        val e = CellImbalanceEvaluator()
        repeat(streak - 1) { e.tick(0.06) }
        assertFalse(e.tick(0.04))         // recovery resets the streak
        assertEquals(0, e.breachStreak)
        repeat(streak - 1) { assertFalse(e.tick(0.06)) }
        assertTrue(e.tick(0.06))          // needs a fresh full streak
    }

    // ── Hysteresis / re-arming ────────────────────────────────────────────────────

    @Test fun `re-arms after a full recovery and can fire a second episode`() {
        val e = CellImbalanceEvaluator()
        assertEquals(1, e.countFires(streak, spread = 0.06))
        assertTrue(e.alerted)

        assertFalse(e.tick(0.04))         // full recovery (below threshold − hysteresis)
        assertFalse(e.alerted)

        repeat(streak - 1) { assertFalse(e.tick(0.06)) }
        assertTrue(e.tick(0.06))          // second episode fires
    }

    @Test fun `a value inside the hysteresis band does not re-arm`() {
        val e = CellImbalanceEvaluator()
        assertEquals(1, e.countFires(streak, spread = 0.06))
        assertTrue(e.alerted)

        assertFalse(e.tick(0.047))        // dips into the band, not a full recovery
        assertEquals(0, e.breachStreak)   // streak still breaks
        assertTrue(e.alerted)             // but stays armed-off

        assertEquals(0, e.countFires(streak * 2, spread = 0.06))  // so it cannot re-fire
        assertTrue(e.alerted)
    }

    // ── SoC guard ─────────────────────────────────────────────────────────────────

    @Test fun `breaches above the high SoC guard never fire`() {
        val e = CellImbalanceEvaluator()
        assertEquals(0, e.countFires(streak * 2, spread = 0.06, soc = 97.0))
        assertEquals(0, e.breachStreak)
    }

    @Test fun `breaches below the low SoC guard never fire`() {
        val e = CellImbalanceEvaluator()
        assertEquals(0, e.countFires(streak * 2, spread = 0.06, soc = 3.0))
        assertEquals(0, e.breachStreak)
    }

    @Test fun `leaving the SoC guard band resets an in-progress streak`() {
        val e = CellImbalanceEvaluator()
        repeat(streak - 1) { e.tick(0.06, soc = 50.0) }
        assertEquals(streak - 1, e.breachStreak)

        assertFalse(e.tick(0.06, soc = 97.0))   // out of band — reset
        assertEquals(0, e.breachStreak)

        assertFalse(e.tick(0.06, soc = 50.0))   // streak restarts from 1
        assertEquals(1, e.breachStreak)
    }

    // ── Validity gate ─────────────────────────────────────────────────────────────

    @Test fun `zero cell readings are ignored and do not break the streak`() {
        val e = CellImbalanceEvaluator()
        repeat(streak - 1) { e.tick(0.06) }
        assertEquals(streak - 1, e.breachStreak)

        // BMS dropout: both cells read 0 — ignored, streak preserved.
        assertFalse(e.evaluate(enabled = true, vMax = 0.0, vMin = 0.0, soc = 50.0, thresholdV = threshold))
        assertEquals(streak - 1, e.breachStreak)

        assertTrue(e.tick(0.06))          // next valid breach completes the streak
    }

    // ── Disabled ──────────────────────────────────────────────────────────────────

    @Test fun `disabling never fires and clears state`() {
        val e = CellImbalanceEvaluator()
        repeat(streak - 1) { e.tick(0.06) }

        assertFalse(e.tick(0.06, enabled = false))   // a disabled tick clears the streak
        assertEquals(0, e.breachStreak)
        assertFalse(e.alerted)

        // Sustained breaches while disabled stay silent.
        var fires = 0
        repeat(streak * 2) { if (e.tick(0.06, enabled = false)) fires++ }
        assertEquals(0, fires)
    }
}

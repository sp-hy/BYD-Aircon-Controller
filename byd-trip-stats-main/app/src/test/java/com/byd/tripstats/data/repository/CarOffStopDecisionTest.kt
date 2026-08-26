package com.byd.tripstats.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [decideCarOffStop] — the rule that decides whether a parked
 * (car-off) active trip is auto-stopped, held for a confirmation prompt, or kept.
 */
class CarOffStopDecisionTest {

    private val timeout = 3 * 60_000L          // 3 min
    private val cap     = 30 * 60_000L         // 30 min

    private fun decide(
        offMs: Long,
        kept: Boolean = false,
        confirmEnabled: Boolean = true,
        uiVisible: Boolean = true
    ) = decideCarOffStop(offMs, timeout, cap, kept, confirmEnabled, uiVisible)

    @Test
    fun `inside the continuation window nothing happens`() {
        assertEquals(CarOffStopAction.WITHIN_WINDOW, decide(offMs = 60_000L))   // 1 min
        assertEquals(CarOffStopAction.WITHIN_WINDOW, decide(offMs = timeout))   // exactly at edge
    }

    @Test
    fun `past timeout with UI on screen holds for confirmation`() {
        assertEquals(CarOffStopAction.HOLD_FOR_CONFIRM, decide(offMs = timeout + 1))
    }

    @Test
    fun `past timeout with no UI on screen ends (legacy auto-stop)`() {
        assertEquals(CarOffStopAction.END, decide(offMs = timeout + 1, uiVisible = false))
    }

    @Test
    fun `feature disabled ends at timeout regardless of UI`() {
        assertEquals(CarOffStopAction.END, decide(offMs = timeout + 1, confirmEnabled = false))
        assertEquals(
            CarOffStopAction.END,
            decide(offMs = timeout + 1, confirmEnabled = false, uiVisible = true)
        )
    }

    @Test
    fun `a kept off-window stays open without re-prompting`() {
        assertEquals(CarOffStopAction.KEEP_HELD, decide(offMs = timeout + 1, kept = true))
        assertEquals(CarOffStopAction.KEEP_HELD, decide(offMs = 20 * 60_000L, kept = true)) // 20 min, under cap
    }

    @Test
    fun `safety cap ends even a kept trip`() {
        assertEquals(CarOffStopAction.END, decide(offMs = cap + 1, kept = true))
        // Cap wins over both keep and the prompt.
        assertEquals(CarOffStopAction.END, decide(offMs = cap + 1, kept = false, uiVisible = true))
    }

    @Test
    fun `prompt still shows when the cap is misconfigured at or below the timeout`() {
        // Regression: a cap == timeout (or below) used to fire on the same tick as the
        // prompt and end the trip first, suppressing the dialog. The effective cap is
        // now floored above the timeout, so the prompt always gets a window.
        assertEquals(
            CarOffStopAction.HOLD_FOR_CONFIRM,
            decideCarOffStop(
                offDurationMs = timeout + 1, timeoutMs = timeout, maxKeptMs = timeout,
                userKept = false, confirmEnabled = true, uiVisible = true
            )
        )
        // And a cap well below the timeout still can't pre-empt the prompt.
        assertEquals(
            CarOffStopAction.HOLD_FOR_CONFIRM,
            decideCarOffStop(
                offDurationMs = timeout + 1, timeoutMs = timeout, maxKeptMs = 1_000L,
                userKept = false, confirmEnabled = true, uiVisible = true
            )
        )
    }
}

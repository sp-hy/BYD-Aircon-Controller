package com.byd.tripstats.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardViewModelDistanceTest {

    @Test
    fun `deriveLiveSessionAnchorOdometer back-calculates from journey mileage`() {
        val anchor = DashboardViewModel.deriveLiveSessionAnchorOdometer(
            odometerKm = 12345.6,
            journeyDistanceKm = 12.3
        )

        assertEquals(12333.3, anchor, 0.0001)
    }

    @Test
    fun `resolveLiveSessionDistanceKm falls back to odometer anchor when journey is absent`() {
        val distance = DashboardViewModel.resolveLiveSessionDistanceKm(
            odometerKm = 12345.6,
            anchorOdometerKm = 12340.1,
            journeyDistanceKm = null
        )

        assertEquals(5.5, distance, 0.0001)
    }

    @Test
    fun `resolveLiveSessionDistanceKm uses odometer delta when anchor is present, ignoring journey mileage`() {
        val distance = DashboardViewModel.resolveLiveSessionDistanceKm(
            odometerKm = 12345.6,
            anchorOdometerKm = 12340.1,
            journeyDistanceKm = 6.2
        )

        assertEquals(5.5, distance, 0.0001)
    }

    @Test
    fun `usable journey distance rejects zero but accepts positive values`() {
        // 0.0 and values close to zero are treated as "not yet received" — not usable.
        // This prevents deriving a wrong anchor when the instrument cluster hasn't sent
        // journey distance data yet after car restart.
        assertTrue(!DashboardViewModel.isUsableJourneyDistance(0.0))
        assertTrue(!DashboardViewModel.isUsableJourneyDistance(0.005))
        assertTrue(DashboardViewModel.isUsableJourneyDistance(0.02))
        assertTrue(DashboardViewModel.isUsableJourneyDistance(3.7))
    }

    @Test
    fun `integrateDistanceKm converts speed and delta time into kilometers`() {
        val distance = DashboardViewModel.integrateDistanceKm(
            speedKmh = 72.0,
            deltaSeconds = 5.0
        )

        assertEquals(0.1, distance, 0.0001)
    }
}

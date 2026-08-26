package com.byd.tripstats.data.analysis

import com.byd.tripstats.data.config.CarCatalog
import com.byd.tripstats.data.local.entity.TripDataPointEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripEnergyBreakdownTest {

    /**
     * Two points, 1-hour interval, 100 m climb with Atto 3 kerb mass.
     *  - Climb energy: 1825 × 9.80665 × 100 / 3_600_000 ≈ 0.497 kWh
     *  - Rolling resistance and aero are also computed (car has mass and CdA)
     *  - Total consumed = 6.0 kWh (discharge delta)
     */
    @Test
    fun calculatesGradientRollingAndAero() {
        val car = CarCatalog.BYD_ATTO_3
        val points = listOf(
            point(timestamp = 0L,           altitude = 100.0, speed = 60.0, totalDischarge = 10.0),
            point(timestamp = 3_600_000L,   altitude = 200.0, speed = 60.0, totalDischarge = 16.0)
        )

        val breakdown = calculateTripEnergyBreakdown(dataPoints = points, carConfig = car)

        assertNotNull(breakdown)
        breakdown!!

        assertEquals(6.0, breakdown.totalConsumedKwh, 0.0001)
        assertTrue(breakdown.hasGradientEstimate)
        assertTrue(breakdown.hasPhysicsBreakdown)
        assertTrue(breakdown.hasAeroEstimate)

        // Climb: m × g × Δh / J_per_kWh / η = 1825 × 9.80665 × 100 / 3_600_000 / 0.88 ≈ 0.565 kWh
        assertEquals(0.5650, breakdown.climbKwh, 0.001)
        assertEquals(0.0, breakdown.descentKwh, 0.0001)
        assertEquals(0.5650, breakdown.netGradientKwh, 0.001)

        // Rolling resistance should be positive (car has mass and non-zero distance)
        assertTrue(breakdown.rollingResistanceKwh > 0.0)
        // Aero should be positive (car has CdA and speed > 0)
        assertTrue(breakdown.aeroDragKwh > 0.0)
        // Drivetrain losses are non-negative (kinetic term removed — OBD speed noise)
        assertTrue(breakdown.drivetrainLossesKwh >= 0.0)
    }

    /**
     * No car config → physics breakdown unavailable; breakdown still returns
     * total consumed from the discharge delta.
     */
    @Test
    fun returnsConsumedWithoutPhysicsWhenNoCarConfig() {
        val points = listOf(
            point(timestamp = 0L,         altitude = 100.0, totalDischarge = 10.0),
            point(timestamp = 1_800_000L, altitude =  95.0, totalDischarge = 12.0)
        )

        val breakdown = calculateTripEnergyBreakdown(dataPoints = points, carConfig = null)

        assertNotNull(breakdown)
        breakdown!!

        assertEquals(2.0, breakdown.totalConsumedKwh, 0.0001)
        assertFalse(breakdown.hasPhysicsBreakdown)
        assertFalse(breakdown.hasGradientEstimate)
        assertFalse(breakdown.hasAeroEstimate)
        assertEquals(0.0, breakdown.rollingResistanceKwh, 0.0001)
        assertEquals(0.0, breakdown.aeroDragKwh, 0.0001)
        assertEquals(0.0, breakdown.climbKwh, 0.0001)
    }

    /**
     * Flat trip with known mass → rolling resistance should match
     * Crr × m × g × distance, and drivetrain losses absorb the rest.
     */
    @Test
    fun rollingResistanceMatchesFormula() {
        val car = CarCatalog.BYD_SEAL_DYNAMIC_RWD  // mass 2045 kg
        val speedKmh = 80.0
        val durationHours = 1.0
        val distanceM = speedKmh / 3.6 * durationHours * 3600.0  // 80 000 m

        val points = listOf(
            point(timestamp = 0L,                              altitude = 50.0, speed = speedKmh, totalDischarge = 0.0),
            point(timestamp = (durationHours * 3_600_000).toLong(), altitude = 50.0, speed = speedKmh, totalDischarge = 15.0)
        )

        val breakdown = calculateTripEnergyBreakdown(dataPoints = points, carConfig = car)!!

        // Crr=0.0074, η=0.88 — must match TripEnergyBreakdown constants
        val expectedRollingJ = 0.0074 * 2045.0 * 9.80665 * distanceM
        val expectedRollingKwh = expectedRollingJ / 3_600_000.0 / 0.88
        assertEquals(expectedRollingKwh, breakdown.rollingResistanceKwh, 0.001)
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private fun point(
        timestamp: Long,
        altitude: Double,
        speed: Double = 0.0,
        totalDischarge: Double,
    ) = TripDataPointEntity(
        tripId         = 1,
        timestamp      = timestamp,
        latitude       = 0.0,
        longitude      = 0.0,
        altitude       = altitude,
        speed          = speed,
        power          = 0.0,
        soc            = 50.0,
        odometer       = 0.0,
        batteryTemp    = 0.0,
        totalDischarge = totalDischarge,
        gear           = "D",
        isRegenerating = false,
        rawJson        = "{}"
    )
}

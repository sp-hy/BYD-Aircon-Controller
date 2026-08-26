package com.byd.tripstats.ui.components

import com.byd.tripstats.data.local.entity.TripDataPointEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class CondensedChartsTest {

    @Test
    fun condenseForSpeed_preserves_trough_and_peak_within_bucket() {
        // Min/max decimation: each bucket emits BOTH its slowest and fastest row (in
        // chronological order), so a brief stop survives alongside the peak. With maxPoints=4
        // the budget is two output points per bucket across two buckets of four rows each.
        val points = listOf(
            point(id = 1, timestamp = 1_000L, speed = 80.0),
            point(id = 2, timestamp = 2_000L, speed = 173.0),  // bucket A peak (earlier)
            point(id = 3, timestamp = 3_000L, speed = 40.0),   // bucket A trough (later)
            point(id = 4, timestamp = 4_000L, speed = 90.0),
            point(id = 5, timestamp = 5_000L, speed = 120.0),  // bucket B peak (earlier)
            point(id = 6, timestamp = 6_000L, speed = 100.0),
            point(id = 7, timestamp = 7_000L, speed = 30.0),   // bucket B trough (later)
            point(id = 8, timestamp = 8_000L, speed = 95.0)
        )
        val condensed = condenseForSpeed(points, maxPoints = 4)
        // Both extremes of each bucket are kept, peak-then-trough here since the peak occurs
        // earlier in both buckets and emission follows the original time order.
        assertEquals(listOf(173.0, 40.0, 120.0, 30.0), condensed.map { it.speed })
    }

    @Test
    fun condenseForPower_preserves_peak_absolute_power_within_bucket() {
        val points = listOf(
            point(id = 1, timestamp = 1_000L, power = 100.0),
            point(id = 2, timestamp = 2_000L, power = 295.0),
            point(id = 3, timestamp = 3_000L, power = -80.0),
            point(id = 4, timestamp = 4_000L, power = -200.0)
        )
        val condensed = condenseForPower(points, maxPoints = 2)
        assertEquals(2, condensed.size)
        assertEquals(295.0, condensed[0].power, 0.001)
        assertEquals(-200.0, condensed[1].power, 0.001)
    }

    @Test
    fun condenseForRpm_preserves_each_axle_peak_within_bucket() {
        val points = listOf(
            rpmPoint(id = 1, timestamp = 1_000L, front = 0, rear = 4200),
            rpmPoint(id = 2, timestamp = 2_000L, front = 1800, rear = 0),
            rpmPoint(id = 3, timestamp = 3_000L, front = 0, rear = 3900),
            rpmPoint(id = 4, timestamp = 4_000L, front = 2200, rear = 0)
        )

        val condensed = condenseForRpm(points, maxPoints = 2)

        assertEquals(2, condensed.size)
        assertEquals(1800, condensed[0].engineSpeedFront)
        assertEquals(4200, condensed[0].engineSpeedRear)
        assertEquals(2200, condensed[1].engineSpeedFront)
        assertEquals(3900, condensed[1].engineSpeedRear)
    }

    private fun point(
        id: Long,
        timestamp: Long,
        speed: Double = 0.0,
        power: Double = 0.0
    ) = TripDataPointEntity(
        id = id,
        tripId = 1L,
        timestamp = timestamp,
        latitude = 0.0,
        longitude = 0.0,
        altitude = 0.0,
        speed = speed,
        power = power,
        soc = 80.0,
        odometer = 1000.0,
        batteryTemp = 25.0,
        totalDischarge = 500.0,
        gear = "D",
        isRegenerating = false
    )

    private fun rpmPoint(
        id: Long,
        timestamp: Long,
        front: Int,
        rear: Int
    ) = TripDataPointEntity(
        id = id,
        tripId = 1L,
        timestamp = timestamp,
        latitude = 0.0,
        longitude = 0.0,
        altitude = 0.0,
        speed = 0.0,
        power = 0.0,
        soc = 80.0,
        odometer = 1000.0,
        batteryTemp = 25.0,
        totalDischarge = 500.0,
        gear = "D",
        isRegenerating = false,
        engineSpeedFront = front,
        engineSpeedRear = rear
    )
}

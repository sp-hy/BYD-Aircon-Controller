package com.byd.tripstats.ui.components

import com.byd.tripstats.data.local.entity.TripDataPointEntity
import com.byd.tripstats.data.local.entity.TripEntity
import com.byd.tripstats.ui.components.routeanalysis.buildDriveModeSummaries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteAnalysisTabTest {

    @Test
    fun singleMode_summary_matches_trip_overview_consumption() {
        val trip = trip(
            endOdometer = 1008.0,
            endTotalDischarge = 501.8
        )
        val points = listOf(
            point(timestamp = 0L, speed = 40.0, power = 12.0, driveMode = 1),
            point(timestamp = 10_000L, speed = 40.0, power = 12.0, driveMode = 1),
            point(timestamp = 20_000L, speed = 40.0, power = 12.0, driveMode = 1)
        )

        val summaries = buildDriveModeSummaries(points, trip)

        assertEquals(1, summaries.size)
        assertEquals(trip.distance!!, summaries.single().distanceKm, 0.0001)
        assertEquals(trip.efficiency!!, summaries.single().consumptionKwhPer100Km!!, 0.0001)
    }

    @Test
    fun multiMode_weighted_average_matches_trip_overview_consumption() {
        val trip = trip(
            endOdometer = 1010.0,
            endTotalDischarge = 502.2
        )
        val points = listOf(
            point(timestamp = 0L, speed = 36.0, power = 10.0, driveMode = 1),
            point(timestamp = 10_000L, speed = 36.0, power = 10.0, driveMode = 1),
            point(timestamp = 20_000L, speed = 72.0, power = 20.0, driveMode = 2),
            point(timestamp = 30_000L, speed = 72.0, power = 20.0, driveMode = 2),
            point(timestamp = 40_000L, speed = 72.0, power = 20.0, driveMode = 2)
        )

        val summaries = buildDriveModeSummaries(points, trip)
        val weightedAverage = summaries.sumOf {
            (it.consumptionKwhPer100Km ?: 0.0) * it.distanceKm
        } / summaries.sumOf { it.distanceKm }

        assertEquals(2, summaries.size)
        assertTrue(summaries.all { (it.consumptionKwhPer100Km ?: 0.0) > 0.0 })
        assertEquals(trip.efficiency!!, weightedAverage, 0.0001)
    }

    @Test
    fun mode_used_for_fifteen_seconds_is_included() {
        val points = listOf(
            point(timestamp = 0L, speed = 30.0, power = 8.0, driveMode = 1),
            point(timestamp = 10_000L, speed = 30.0, power = 8.0, driveMode = 1),
            point(timestamp = 15_000L, speed = 30.0, power = 8.0, driveMode = 1)
        )

        val summaries = buildDriveModeSummaries(points, trip = null)

        assertEquals(1, summaries.size)
        assertEquals("Eco", summaries.single().label)
    }

    private fun trip(
        endOdometer: Double,
        endTotalDischarge: Double
    ) = TripEntity(
        id = 1L,
        startTime = 0L,
        endTime = 40_000L,
        startOdometer = 1000.0,
        endOdometer = endOdometer,
        startSoc = 80.0,
        endSoc = 78.0,
        startTotalDischarge = 500.0,
        endTotalDischarge = endTotalDischarge,
        isActive = false
    )

    private fun point(
        timestamp: Long,
        speed: Double,
        power: Double,
        driveMode: Int
    ) = TripDataPointEntity(
        id = timestamp + 1,
        tripId = 1L,
        timestamp = timestamp,
        latitude = 0.0,
        longitude = 0.0,
        altitude = 0.0,
        speed = speed,
        power = power,
        soc = 80.0,
        odometer = 1000.0 + (timestamp / 10_000.0) * 0.2,
        batteryTemp = 25.0,
        totalDischarge = 500.0 + (timestamp / 10_000.0) * 0.05,
        gear = "D",
        isRegenerating = power < 0.0,
        rawJson = """{"drive_mode":$driveMode,"regen_mode":1}"""
    )
}

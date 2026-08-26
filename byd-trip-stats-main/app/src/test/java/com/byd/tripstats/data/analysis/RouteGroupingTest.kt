package com.byd.tripstats.data.analysis

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [RouteGrouping]. Pure JVM — no Android, no DB.
 *
 * Coordinate reference: ~0.001° latitude ≈ 111 m, so an offset of 0.001° keeps
 * two points inside the 250 m radius, while 0.01° (~1.1 km) is clearly outside.
 */
class RouteGroupingTest {

    // Home (A) and Work (B) anchor points.
    private val aLat = 52.5000; private val aLon = 13.4000
    private val bLat = 52.5500; private val bLon = 13.4500

    private var nextId = 1L
    private fun trip(
        startTime: Long,
        sLat: Double = aLat, sLon: Double = aLon,
        eLat: Double = bLat, eLon: Double = bLon,
        distanceKm: Double = 10.0,
        efficiency: Double = 16.0,
        durationMs: Long = 20 * 60_000L
    ) = RouteTripInput(
        tripId = nextId++,
        startTime = startTime,
        startLat = sLat, startLon = sLon,
        endLat = eLat, endLon = eLon,
        distanceKm = distanceKm,
        efficiencyKwhPer100km = efficiency,
        durationMs = durationMs,
        energyKwh = distanceKm * efficiency / 100.0
    )

    @Test fun groupsRepeatedSameRoute() {
        val trips = listOf(
            trip(1_000, sLat = aLat + 0.0005, distanceKm = 10.0, efficiency = 15.0),
            trip(2_000, eLat = bLat - 0.0005, distanceKm = 10.2, efficiency = 17.0),
            trip(3_000, distanceKm = 9.9, efficiency = 16.0)
        )
        val groups = RouteGrouping.group(trips)
        assertEquals(1, groups.size)
        val g = groups.single()
        assertEquals(3, g.instanceCount)
        assertEquals(15.0, g.bestEfficiencyKwhPer100km, 1e-9)
        assertEquals(17.0, g.worstEfficiencyKwhPer100km, 1e-9)
        assertEquals(16.0, g.avgEfficiencyKwhPer100km, 1e-9)
        // Instances are newest-first.
        assertEquals(listOf(3_000L, 2_000L, 1_000L), g.instances.map { it.startTime })
        assertEquals(3_000L, g.lastDrivenAt)
        assertEquals(1_000L, g.firstDrivenAt)
    }

    @Test fun fewerThanMinInstancesIsNotRecurring() {
        val trips = listOf(trip(1_000), trip(2_000))   // only twice
        assertTrue(RouteGrouping.group(trips).isEmpty())
    }

    @Test fun oppositeDirectionsAreSeparateRoutes() {
        val there = (1..3).map { trip(it * 1_000L) }                               // A → B
        val back  = (1..3).map { trip(10_000L + it * 1_000L, sLat = bLat, sLon = bLon, eLat = aLat, eLon = aLon) } // B → A
        val groups = RouteGrouping.group(there + back)
        assertEquals(2, groups.size)
        assertTrue(groups.all { it.instanceCount == 3 })
        assertNotEquals(groups[0].id, groups[1].id)
    }

    @Test fun sameEndpointsDifferentDistanceDoNotMerge() {
        // Three ~10 km trips form a route; a 20 km trip with identical endpoints
        // (a detour) starts its own cluster and is filtered out (count 1 < 3).
        val trips = listOf(
            trip(1_000, distanceKm = 10.0),
            trip(2_000, distanceKm = 10.1),
            trip(3_000, distanceKm = 9.9),
            trip(4_000, distanceKm = 20.0)
        )
        val groups = RouteGrouping.group(trips)
        assertEquals(1, groups.size)
        assertEquals(3, groups.single().instanceCount)
    }

    @Test fun farApartEndpointsDoNotMerge() {
        val routeA = (1..3).map { trip(it * 1_000L) }
        // Different city entirely (~1 km+ away on both ends).
        val routeB = (1..3).map {
            trip(10_000L + it * 1_000L,
                sLat = aLat + 0.02, sLon = aLon + 0.02,
                eLat = bLat + 0.02, eLon = bLon + 0.02)
        }
        val groups = RouteGrouping.group(routeA + routeB)
        assertEquals(2, groups.size)
    }

    @Test fun groupsSortedByInstanceCountThenRecency() {
        // Route A driven 4×, route B driven 3× (more recent). A should rank first.
        val routeA = (1..4).map { trip(it * 1_000L) }
        val routeB = (1..3).map {
            trip(100_000L + it * 1_000L,
                sLat = aLat + 0.01, eLat = bLat + 0.01)
        }
        val groups = RouteGrouping.group(routeA + routeB)
        assertEquals(2, groups.size)
        assertEquals(4, groups.first().instanceCount)
    }

    @Test fun haversineAndGpsValidityHelpers() {
        // ~111 m for 0.001° latitude.
        assertEquals(111.0, RouteGrouping.haversineMeters(aLat, aLon, aLat + 0.001, aLon), 2.0)
        assertFalse(RouteGrouping.hasValidGps(0.0, 0.0))     // null island
        assertTrue(RouteGrouping.hasValidGps(aLat, aLon))
    }
}

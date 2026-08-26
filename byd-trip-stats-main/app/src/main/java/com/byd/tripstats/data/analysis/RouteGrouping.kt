package com.byd.tripstats.data.analysis

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Recurring-route detection.
 *
 * Groups completed trips that are really the *same* journey (e.g. a daily commute)
 * so their efficiency can be compared across instances. Two trips belong to the
 * same route when:
 *   • their start points are within [DEFAULT_RADIUS_M] of each other, AND
 *   • their end points are within [DEFAULT_RADIUS_M] of each other, AND
 *   • their total distances are within a tolerance band (guards against two
 *     different paths that happen to share endpoints).
 *
 * Direction matters: A→B and B→A are different routes (start is matched to start
 * and end to end), because efficiency genuinely differs by direction (elevation,
 * traffic, time-of-day temperature).
 *
 * Pure and Android-free so it can be unit-tested on the JVM.
 */
object RouteGrouping {

    /** Endpoints within this many metres are treated as "the same place". */
    const val DEFAULT_RADIUS_M = 250.0

    /** A route must have been driven at least this many times to count as recurring. */
    const val DEFAULT_MIN_INSTANCES = 3

    /** Relative half-width of the distance band: |d − avg| ≤ pct·avg (with a floor). */
    const val DEFAULT_DISTANCE_TOLERANCE_PCT = 0.25

    /** Absolute floor for the distance band so short routes aren't over-strict. */
    const val DISTANCE_TOLERANCE_FLOOR_KM = 1.0

    private const val EARTH_RADIUS_M = 6_371_000.0

    /** Great-circle distance between two lat/lon points, in metres. */
    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        return EARTH_RADIUS_M * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    /** True for coordinates that are a real fix (not unset / "null island"). */
    fun hasValidGps(lat: Double, lon: Double): Boolean =
        lat.isFinite() && lon.isFinite() &&
        lat in -90.0..90.0 && lon in -180.0..180.0 &&
        (abs(lat) > 1e-6 || abs(lon) > 1e-6)

    /**
     * Greedy single-pass clustering. Trips are processed oldest-first so the
     * earliest occurrence seeds each cluster; each subsequent trip joins the first
     * cluster it matches (by running-centroid endpoints + distance band) or starts
     * a new one. Result keeps only clusters with ≥ [minInstances] trips, sorted by
     * how often they're driven (then recency). O(n·clusters) — fine for the trip
     * counts this app holds.
     */
    fun group(
        trips: List<RouteTripInput>,
        radiusM: Double = DEFAULT_RADIUS_M,
        minInstances: Int = DEFAULT_MIN_INSTANCES,
        distanceTolerancePct: Double = DEFAULT_DISTANCE_TOLERANCE_PCT
    ): List<RouteGroup> {
        val clusters = mutableListOf<Cluster>()

        trips.sortedBy { it.startTime }.forEach { trip ->
            val match = clusters.firstOrNull { c ->
                haversineMeters(trip.startLat, trip.startLon, c.centroidStartLat, c.centroidStartLon) <= radiusM &&
                haversineMeters(trip.endLat, trip.endLon, c.centroidEndLat, c.centroidEndLon) <= radiusM &&
                abs(trip.distanceKm - c.avgDistanceKm) <=
                    maxOf(distanceTolerancePct * c.avgDistanceKm, DISTANCE_TOLERANCE_FLOOR_KM)
            }
            (match ?: Cluster().also { clusters.add(it) }).add(trip)
        }

        return clusters
            .filter { it.size >= minInstances }
            .map { it.toRouteGroup() }
            .sortedWith(compareByDescending<RouteGroup> { it.instanceCount }.thenByDescending { it.lastDrivenAt })
    }

    /** Mutable running accumulator for one route cluster. */
    private class Cluster {
        private val trips = mutableListOf<RouteTripInput>()
        private var sumStartLat = 0.0
        private var sumStartLon = 0.0
        private var sumEndLat = 0.0
        private var sumEndLon = 0.0
        private var sumDistanceKm = 0.0

        val size get() = trips.size
        val centroidStartLat get() = sumStartLat / size
        val centroidStartLon get() = sumStartLon / size
        val centroidEndLat get() = sumEndLat / size
        val centroidEndLon get() = sumEndLon / size
        val avgDistanceKm get() = sumDistanceKm / size

        fun add(t: RouteTripInput) {
            trips.add(t)
            sumStartLat += t.startLat; sumStartLon += t.startLon
            sumEndLat += t.endLat; sumEndLon += t.endLon
            sumDistanceKm += t.distanceKm
        }

        fun toRouteGroup(): RouteGroup {
            val instances = trips
                .sortedByDescending { it.startTime }
                .map {
                    RouteInstance(
                        tripId = it.tripId,
                        startTime = it.startTime,
                        efficiencyKwhPer100km = it.efficiencyKwhPer100km,
                        distanceKm = it.distanceKm,
                        durationMs = it.durationMs,
                        energyKwh = it.energyKwh
                    )
                }
            val effs = trips.map { it.efficiencyKwhPer100km }
            return RouteGroup(
                // Stable, human-debuggable id from the rounded centroid endpoints.
                id = "%.4f,%.4f>%.4f,%.4f".format(
                    centroidStartLat, centroidStartLon, centroidEndLat, centroidEndLon
                ),
                startLat = centroidStartLat, startLon = centroidStartLon,
                endLat = centroidEndLat, endLon = centroidEndLon,
                instanceCount = size,
                avgDistanceKm = avgDistanceKm,
                avgEfficiencyKwhPer100km = effs.average(),
                bestEfficiencyKwhPer100km = effs.min(),
                worstEfficiencyKwhPer100km = effs.max(),
                avgDurationMs = (trips.sumOf { it.durationMs } / size),
                lastDrivenAt = trips.maxOf { it.startTime },
                firstDrivenAt = trips.minOf { it.startTime },
                instances = instances
            )
        }
    }
}

/** Minimal per-trip input the grouper needs (decouples it from Room entities). */
data class RouteTripInput(
    val tripId: Long,
    val startTime: Long,
    val startLat: Double,
    val startLon: Double,
    val endLat: Double,
    val endLon: Double,
    val distanceKm: Double,
    val efficiencyKwhPer100km: Double,
    val durationMs: Long,
    val energyKwh: Double
)

/** One occurrence of a recurring route, newest-first within its [RouteGroup]. */
data class RouteInstance(
    val tripId: Long,
    val startTime: Long,
    val efficiencyKwhPer100km: Double,
    val distanceKm: Double,
    val durationMs: Long,
    val energyKwh: Double
)

/** A set of trips that share the same route (same start, end and ~distance). */
data class RouteGroup(
    val id: String,
    val startLat: Double,
    val startLon: Double,
    val endLat: Double,
    val endLon: Double,
    val instanceCount: Int,
    val avgDistanceKm: Double,
    val avgEfficiencyKwhPer100km: Double,
    val bestEfficiencyKwhPer100km: Double,
    val worstEfficiencyKwhPer100km: Double,
    val avgDurationMs: Long,
    val lastDrivenAt: Long,
    val firstDrivenAt: Long,
    val instances: List<RouteInstance>
)

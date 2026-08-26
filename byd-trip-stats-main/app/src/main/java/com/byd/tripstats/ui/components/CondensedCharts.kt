package com.byd.tripstats.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.byd.tripstats.data.local.entity.TripDataPointEntity
import kotlin.math.abs

/**
 * Condensed versions of charts for overview display
 * These charts:
 * - Sample data to 36 points max
 * - Disable scrolling/zooming
 * - Fit entire trip in viewport
 */

@Composable
fun CondensedEnergyChart(
    dataPoints: List<TripDataPointEntity>,
    modifier: Modifier = Modifier
) {
    // Energy is integrated (power × dt) INSIDE the chart, so it must see full-resolution
    // samples: condensing the raw points first would hand the per-interval gap-guard
    // (dt ≤ 60s) enormous gaps and collapse the curve to a flat line on long trips. The
    // chart integrates at full resolution and decimates the resulting monotone curve itself.
    EnergyConsumptionChart(dataPoints = dataPoints, modifier = modifier, maxPoints = 36)
}

@Composable
fun CondensedSpeedChart(
    dataPoints: List<TripDataPointEntity>,
    useImperial: Boolean = false,
    modifier: Modifier = Modifier
) {
    val condensed = remember(dataPoints) { condenseForSpeed(dataPoints) }
    SpeedChart(dataPoints = condensed, useImperial = useImperial, modifier = modifier)
}

@Composable
fun CondensedMotorRpmChart(
    dataPoints: List<TripDataPointEntity>,
    modifier: Modifier = Modifier
) {
    val condensed = remember(dataPoints) { condenseForRpm(dataPoints) }
    MotorRpmChart(dataPoints = condensed, modifier = modifier)
}

// ── Condensing strategies ────────────────────────────────────────────────────
// Two bucketing shapes cover every chart, so the (fiddly) bucketing math lives here
// exactly once instead of being copy-pasted per signal:
//   • condenseBuckets — one representative row per bucket; for slow, monotone signals.
//   • condenseMinMax  — the trough AND peak row of each bucket, at their real positions;
//                       for spiky signals where dropping either extreme loses meaning.

/**
 * Split [dataPoints] into at most [maxPoints] contiguous, equal-size buckets and reduce each
 * bucket to a single representative row via [reduce]. (Ceiling division guarantees the bucket
 * count never exceeds [maxPoints]; the trailing take is belt-and-suspenders.)
 */
private inline fun condenseBuckets(
    dataPoints: List<TripDataPointEntity>,
    maxPoints: Int,
    reduce: (bucket: List<TripDataPointEntity>) -> TripDataPointEntity
): List<TripDataPointEntity> {
    if (dataPoints.size <= maxPoints) return dataPoints
    val step = (dataPoints.size + maxPoints - 1) / maxPoints
    return dataPoints.chunked(step).map(reduce).take(maxPoints)
}

/**
 * Min/max decimation: keep BOTH the lowest and highest [selector] row of each bucket, emitted
 * in chronological order. Unlike "keep one row per bucket", this preserves each bucket's trough
 * and peak at (near) their true x-position — a brief stop or a full-throttle/regen burst no
 * longer vanishes into the winning sample. Each bucket emits up to two rows, so the budget is
 * spent on half as many buckets to stay within [maxPoints].
 */
private inline fun condenseMinMax(
    dataPoints: List<TripDataPointEntity>,
    maxPoints: Int,
    selector: (TripDataPointEntity) -> Float
): List<TripDataPointEntity> {
    if (dataPoints.size <= maxPoints) return dataPoints
    val buckets = (maxPoints / 2).coerceAtLeast(1)
    val step = (dataPoints.size + buckets - 1) / buckets
    val out = ArrayList<TripDataPointEntity>(maxPoints)
    var i = 0
    while (i < dataPoints.size) {
        val end = minOf(i + step, dataPoints.size)
        var lo = i; var hi = i
        var loV = selector(dataPoints[i]); var hiV = loV
        var j = i + 1
        while (j < end) {
            val v = selector(dataPoints[j])
            if (v < loV) { lo = j; loV = v }
            if (v > hiV) { hi = j; hiV = v }
            j++
        }
        // Emit both extrema at their real rows, earliest first, so the line stays time-ordered.
        when {
            lo == hi -> out.add(dataPoints[lo])
            lo < hi  -> { out.add(dataPoints[lo]); out.add(dataPoints[hi]) }
            else     -> { out.add(dataPoints[hi]); out.add(dataPoints[lo]) }
        }
        i = end
    }
    return out
}

/**
 * Condense data points to a maximum number of points (plain decimation, anchored on each
 * bucket's middle row). Suitable for slow-moving signals (SoC, altitude, voltage, tyre).
 * @param dataPoints The original data points
 * @param maxPoints Maximum number of points to return (default: 36)
 */
fun condenseData(
    dataPoints: List<TripDataPointEntity>,
    maxPoints: Int = 36
): List<TripDataPointEntity> =
    condenseBuckets(dataPoints, maxPoints) { bucket -> bucket[bucket.size / 2] }

/** Speed is spiky and stops matter: keep each bucket's slowest AND fastest row. */
fun condenseForSpeed(
    dataPoints: List<TripDataPointEntity>,
    maxPoints: Int = 36
): List<TripDataPointEntity> =
    condenseMinMax(dataPoints, maxPoints) { it.speed.toFloat() }

/** Power swings both ways: keep each bucket's peak regen AND peak acceleration row. */
fun condenseForPower(
    dataPoints: List<TripDataPointEntity>,
    maxPoints: Int = 36
): List<TripDataPointEntity> =
    condenseMinMax(dataPoints, maxPoints) { it.power.toFloat() }

/**
 * RPM is an event-driven field: between feature events the DB-written value can be 0 for
 * individual rows while the motors are actually spinning, and on AWD cars the front and rear
 * peaks often land on different rows — so neither axle's burst may be dropped. Each bucket
 * collapses to its middle row but carries BOTH axles' peak magnitude. Unlike speed/power this
 * can't place each peak at its own true row (two series, one point), so the middle row anchors
 * the bucket's x-position.
 */
fun condenseForRpm(
    dataPoints: List<TripDataPointEntity>,
    maxPoints: Int = 36
): List<TripDataPointEntity> =
    condenseBuckets(dataPoints, maxPoints) { bucket ->
        bucket[bucket.size / 2].copy(
            engineSpeedFront = bucket.maxOf { abs(it.engineSpeedFront) },
            engineSpeedRear = bucket.maxOf { abs(it.engineSpeedRear) }
        )
    }

@Composable
fun CondensedAltitudeChart(
    dataPoints: List<TripDataPointEntity>,
    modifier: Modifier = Modifier
) {
    val condensed = remember(dataPoints) { condenseData(dataPoints) }
    AltitudeChart(dataPoints = condensed, modifier = modifier)
}

@Composable
fun CondensedSocChart(
    dataPoints: List<TripDataPointEntity>,
    modifier: Modifier = Modifier
) {
    val condensed = remember(dataPoints) { condenseData(dataPoints) }
    SocChart(dataPoints = condensed, modifier = modifier)
}

@Composable
fun CondensedPowerChart(
    dataPoints: List<TripDataPointEntity>,
    modifier: Modifier = Modifier
) {
    val condensed = remember(dataPoints) { condenseForPower(dataPoints) }
    PowerChart(dataPoints = condensed, modifier = modifier)
}

@Composable
fun CondensedBatteryVoltageChart(
    dataPoints: List<TripDataPointEntity>,
    modifier: Modifier = Modifier
) {
    val condensed = remember(dataPoints) { condenseData(dataPoints) }
    BatteryVoltageChart(dataPoints = condensed, modifier = modifier)
}

@Composable
fun CondensedTyrePressureChart(
    dataPoints: List<TripDataPointEntity>,
    modifier: Modifier = Modifier
) {
    val condensed = remember(dataPoints) { condenseData(dataPoints) }
    TyrePressureChart(dataPoints = condensed, modifier = modifier)
}

@Composable
fun CondensedInstantConsumptionChart(
    dataPoints: List<TripDataPointEntity>,
    useImperial: Boolean = false,
    modifier: Modifier = Modifier
) {
    // No condensing needed — InstantConsumptionChart already filters to driving points
    InstantConsumptionChart(dataPoints = dataPoints, useImperial = useImperial, modifier = modifier)
}

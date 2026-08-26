package com.byd.tripstats.ui.components.routeanalysis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.tripstats.R
import com.byd.tripstats.data.local.entity.TripDataPointEntity
import com.byd.tripstats.data.local.entity.TripEntity
import com.byd.tripstats.data.preferences.UnitSystem
import com.byd.tripstats.data.preferences.consumptionUnit
import com.byd.tripstats.data.preferences.convertDistance
import com.byd.tripstats.data.preferences.convertEfficiency
import com.byd.tripstats.data.preferences.distanceUnit
import com.byd.tripstats.ui.components.driveModeColor
import com.byd.tripstats.ui.components.driveModeLabel
import com.byd.tripstats.ui.components.extractTripModes
import com.byd.tripstats.ui.components.hasTripModeData
import com.byd.tripstats.ui.components.regenModeColor
import com.byd.tripstats.ui.components.regenModeLabel
import kotlin.math.abs
import kotlin.math.max

internal data class ModeSummary(
    val label: String,
    val color: Color,
    val distanceKm: Double,
    val distanceSharePct: Double,
    val durationMinutes: Double,
    val consumptionKwhPer100Km: Double?,
    val regenSharePct: Double?
)

@Composable
internal fun ModeInsightsCard(
    dataPoints: List<TripDataPointEntity>,
    trip: TripEntity?,
    useImperial: Boolean = false
) {
    if (!hasTripModeData(dataPoints)) return

    val driveSummaries = remember(dataPoints, trip) {
        buildDriveModeSummaries(dataPoints = dataPoints, trip = trip)
    }
    val regenSummaries = remember(dataPoints, trip) {
        buildRegenModeSummaries(dataPoints = dataPoints, trip = trip)
    }
    val strDriveSameFmt         = stringResource(R.string.mode_drive_modes_same)
    val strDriveMoreEfficient   = stringResource(R.string.mode_drive_more_efficient)
    val strRegenSameFmt         = stringResource(R.string.mode_regen_modes_same)
    val strRegenMoreFmt         = stringResource(R.string.mode_regen_more)
    val insights: List<String> = remember(driveSummaries, regenSummaries,
        strDriveSameFmt, strDriveMoreEfficient, strRegenSameFmt, strRegenMoreFmt) {
        buildList {
            compareDriveModes(driveSummaries, strDriveSameFmt, strDriveMoreEfficient)?.let(::add)
            compareRegenModes(regenSummaries, strRegenSameFmt, strRegenMoreFmt)?.let(::add)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().then(cardBorder),
        colors = cardColors
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.mode_insights_label),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.mode_insights_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            ModeStackedBar(label = stringResource(R.string.mode_drive_label), summaries = driveSummaries)
            if (regenSummaries.isNotEmpty()) {
                ModeStackedBar(label = stringResource(R.string.mode_regen_label), summaries = regenSummaries)
            }

            if (insights.isNotEmpty()) {
                insights.forEach { insight: String ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = insight,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            if (driveSummaries.isNotEmpty()) {
                Text(stringResource(R.string.drive_mode_usage_label), fontWeight = FontWeight.SemiBold)
                driveSummaries.forEach { summary: ModeSummary ->
                    ModeSummaryRow(summary, useImperial)
                }
            }

            if (regenSummaries.isNotEmpty()) {
                Text(stringResource(R.string.regen_mode_usage_label), fontWeight = FontWeight.SemiBold)
                regenSummaries.forEach { summary: ModeSummary ->
                    ModeSummaryRow(summary, useImperial)
                }
            }
        }
    }
}

@Composable
private fun ModeStackedBar(
    label: String,
    summaries: List<ModeSummary>
) {
    if (summaries.isEmpty()) return
    val total = summaries.sumOf { it.distanceKm }.takeIf { it > 0.0 } ?: return

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(999.dp))
        ) {
            summaries.forEach { s ->
                val frac = (s.distanceKm / total).toFloat().coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .weight(frac.coerceAtLeast(0.001f))
                        .fillMaxHeight()
                        .background(s.color)
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            summaries.forEach { s ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(s.color)
                    )
                    Text(
                        text = "${s.label} ${String.format("%.0f", s.distanceSharePct)}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeSummaryRow(summary: ModeSummary, useImperial: Boolean = false) {
    val unitSystem = if (useImperial) UnitSystem.IMPERIAL else UnitSystem.METRIC
    val strOfModeAttributedTrip = stringResource(R.string.mode_of_mode_attributed_trip)
    val strRecoveredViaRegen    = stringResource(R.string.mode_recovered_via_regen)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.05f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(summary.color, RoundedCornerShape(999.dp))
            )
            Column {
                Text(summary.label, fontWeight = FontWeight.Bold)
                Text(
                    text = "${String.format("%.1f", unitSystem.convertDistance(summary.distanceKm))} ${unitSystem.distanceUnit} • ${String.format("%.0f", summary.distanceSharePct)}% $strOfModeAttributedTrip",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = summary.consumptionKwhPer100Km?.let { "${String.format("%.1f", unitSystem.convertEfficiency(it))} ${unitSystem.consumptionUnit}" } ?: "—",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = summary.regenSharePct?.let { "${String.format("%.1f", it)}% $strRecoveredViaRegen" } ?: "—",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

internal fun buildDriveModeSummaries(
    dataPoints: List<TripDataPointEntity>,
    trip: TripEntity?
): List<ModeSummary> =
    buildModeSummaries(
        dataPoints    = dataPoints,
        trip          = trip,
        keySelector   = { it.extractTripModes().driveMode },
        labelSelector = ::driveModeLabel,
        colorSelector = ::driveModeColor
    )

internal fun buildRegenModeSummaries(
    dataPoints: List<TripDataPointEntity>,
    trip: TripEntity?
): List<ModeSummary> =
    buildModeSummaries(
        dataPoints    = dataPoints,
        trip          = trip,
        keySelector   = { it.extractTripModes().regenMode },
        labelSelector = ::regenModeLabel,
        colorSelector = ::regenModeColor
    )

private fun buildModeSummaries(
    dataPoints   : List<TripDataPointEntity>,
    trip         : TripEntity?,
    keySelector  : (TripDataPointEntity) -> Int,
    labelSelector: (Int) -> String,
    colorSelector: (Int) -> Color
): List<ModeSummary> {
    data class Bucket(
        var distanceKm: Double = 0.0,
        var durationMinutes: Double = 0.0,
        var netEnergyKwh: Double = 0.0,
        var tractionKwh: Double = 0.0,
        var regenKwh: Double = 0.0
    )

    val tripDistanceKm = trip?.distance?.takeIf { it.isFinite() && it >= 0.0 }
        ?: if (dataPoints.size >= 2) {
            (dataPoints.last().odometer - dataPoints.first().odometer).coerceAtLeast(0.0)
        } else 0.0
    val tripEnergyKwh = trip?.energyConsumed?.takeIf { it.isFinite() && it >= 0.0 }

    val buckets = mutableMapOf<Int, Bucket>()
    dataPoints.zipWithNext { a, b ->
        val mode = keySelector(a)
        if (mode == 0) return@zipWithNext
        val bucket = buckets.getOrPut(mode) { Bucket() }
        val dtMs = (b.timestamp - a.timestamp).coerceIn(0L, 10_000L)
        val dtHours = dtMs / 3_600_000.0
        val dtMinutes = dtHours * 60.0
        val powerKw = a.power
        val avgSpeedKmh = (a.speed + b.speed) / 2.0
        val distance = avgSpeedKmh * dtHours
        bucket.distanceKm += distance
        bucket.durationMinutes += dtMinutes
        if (powerKw > 0.0) bucket.tractionKwh += powerKw * dtHours
        if (powerKw < 0.0) bucket.regenKwh += abs(powerKw) * dtHours
    }
    buckets.values.forEach { b ->
        b.netEnergyKwh = (b.tractionKwh - b.regenKwh).coerceAtLeast(0.0)
    }

    val visibleBuckets = buckets.filterValues { it.durationMinutes >= MODE_SUMMARY_MIN_DURATION_MINUTES }
    val rawTotalDistance = visibleBuckets.values.sumOf { it.distanceKm }.takeIf { it > 0.0 } ?: 1.0
    val scale = if (tripDistanceKm > 0.0 && rawTotalDistance > 0.0) tripDistanceKm / rawTotalDistance else 1.0
    val rawTotalEnergy = visibleBuckets.values.sumOf { it.netEnergyKwh }
    val energyScale = if (tripEnergyKwh != null && tripEnergyKwh > 0.0 && rawTotalEnergy > 0.0) {
        tripEnergyKwh / rawTotalEnergy
    } else {
        1.0
    }

    return visibleBuckets
        .mapNotNull { (mode, bucket) ->
            val scaledDistance = bucket.distanceKm * scale
            val scaledEnergy   = bucket.netEnergyKwh * energyScale
            val consumption = if (scaledDistance >= 0.1) (scaledEnergy / scaledDistance) * 100.0 else null
            val regenShare  = if (bucket.tractionKwh + bucket.regenKwh > 0.0) {
                (bucket.regenKwh / (bucket.tractionKwh + bucket.regenKwh)) * 100.0
            } else null
            ModeSummary(
                label                 = labelSelector(mode),
                color                 = colorSelector(mode),
                distanceKm            = scaledDistance,
                distanceSharePct      = (bucket.distanceKm / rawTotalDistance) * 100.0,
                durationMinutes       = bucket.durationMinutes,
                consumptionKwhPer100Km = consumption,
                regenSharePct         = regenShare
            )
        }
        .sortedByDescending { it.distanceKm }
}

/**
 * Compares whichever two drive modes were actually driven the most on this trip, purely from
 * measured consumption — no assumption about which mode "should" be more efficient (you can drive
 * Eco hard or Sport gently, so the data leads). Whenever two modes each cover a meaningful distance
 * there is always an insight; a single mode yields none.
 */
private fun compareDriveModes(
    summaries: List<ModeSummary>,
    driveSameFmt: String,
    driveMoreEfficientFmt: String
): String? {
    val ranked = summaries
        .filter { it.distanceKm >= 1.0 && it.consumptionKwhPer100Km != null }
        .sortedByDescending { it.distanceKm }
    if (ranked.size < 2) return null

    val a = ranked[0]
    val b = ranked[1]
    val ca = a.consumptionKwhPer100Km!!
    val cb = b.consumptionKwhPer100Km!!
    val higher = max(ca, cb)
    if (higher <= 0.0) return null

    val diffPct = (abs(ca - cb) / higher) * 100.0
    if (diffPct < 5.0) {
        return driveSameFmt.format(a.label, b.label)
    }
    val lower = if (ca < cb) a else b
    val higherMode = if (ca < cb) b else a
    return driveMoreEfficientFmt.format(lower.label, String.format("%.0f", diffPct), higherMode.label)
}

/**
 * Compares the two most-driven regen modes on this trip by measured regen share — again data-led,
 * so "High" is only reported as better when it actually recovered more here. Any two modes with a
 * meaningful distance always produce an insight.
 */
private fun compareRegenModes(
    summaries: List<ModeSummary>,
    regenSameFmt: String,
    regenMoreFmt: String
): String? {
    val ranked = summaries
        .filter { it.distanceKm >= 1.0 && it.regenSharePct != null }
        .sortedByDescending { it.distanceKm }
    if (ranked.size < 2) return null

    val a = ranked[0]
    val b = ranked[1]
    val ra = a.regenSharePct!!
    val rb = b.regenSharePct!!

    val delta = abs(ra - rb)
    if (delta < 2.0) {
        return regenSameFmt.format(a.label, b.label)
    }
    val more = if (ra > rb) a else b
    val less = if (ra > rb) b else a
    return regenMoreFmt.format(more.label, String.format("%.0f", delta), less.label)
}

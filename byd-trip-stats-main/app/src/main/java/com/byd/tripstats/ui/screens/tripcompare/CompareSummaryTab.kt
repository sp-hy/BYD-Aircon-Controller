package com.byd.tripstats.ui.screens.tripcompare

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.byd.tripstats.R
import com.byd.tripstats.data.local.entity.TripEntity
import com.byd.tripstats.data.preferences.SocSource
import com.byd.tripstats.data.preferences.UnitSystem
import com.byd.tripstats.data.preferences.consumptionUnit
import com.byd.tripstats.data.preferences.convertDistance
import com.byd.tripstats.data.preferences.convertEfficiency
import com.byd.tripstats.data.preferences.distanceUnit
import com.byd.tripstats.data.preferences.speedUnit
import com.byd.tripstats.ui.viewmodel.DashboardViewModel

@Composable
internal fun CompareSummaryTab(
    trips         : List<TripEntity>,
    displayMetrics: Map<Long, DashboardViewModel.TripDisplayMetrics>,
    compareStats  : List<com.byd.tripstats.data.local.entity.TripStatsEntity>,
    visibleTrips  : Set<Int>,
    unitSystem    : UnitSystem = UnitSystem.METRIC,
    socSource     : SocSource  = SocSource.PANEL
) {
    val statsById = compareStats.associateBy { it.tripId }

    fun bestIndices(rawValues: List<Double?>, lowerIsBetter: Boolean): List<Boolean> {
        val visibleVals = rawValues.mapIndexed { i, v -> if (i in visibleTrips) v else null }
        val valid = visibleVals.filterNotNull()
        if (valid.isEmpty()) return rawValues.map { false }
        val best = if (lowerIsBetter) valid.min() else valid.max()
        return rawValues.mapIndexed { i, v ->
            i in visibleTrips && v != null && v == best
        }
    }

    data class MetricRow(val label: String, val values: List<String>, val winners: List<Boolean>)

    val rows = listOf(
        MetricRow(stringResource(R.string.stat_distance),
            trips.map { "%.1f ${unitSystem.distanceUnit}".format(unitSystem.convertDistance(it.distance ?: 0.0)) },
            bestIndices(trips.map { it.distance }, false)),
        MetricRow(stringResource(R.string.stat_duration),
            trips.map { formatDurationCompare(it.duration ?: 0L) },
            bestIndices(trips.map { it.duration?.toDouble() }, false)),
        MetricRow(stringResource(R.string.stat_avg_consumption),
            trips.map { "%.1f ${unitSystem.consumptionUnit}".format(unitSystem.convertEfficiency(it.efficiency ?: 0.0)) },
            bestIndices(trips.map { it.efficiency }, true)),
        MetricRow(stringResource(R.string.stat_energy_consumed),
            trips.map { "%.2f kWh".format(it.energyConsumed ?: 0.0) },
            bestIndices(trips.map { it.energyConsumed }, true)),
        MetricRow(stringResource(R.string.stat_max_speed),
            trips.map { "${it.maxSpeed.toInt()} ${unitSystem.speedUnit}" },
            bestIndices(trips.map { it.maxSpeed }, false)),
        MetricRow(stringResource(R.string.stat_avg_speed),
            trips.map { displayMetrics[it.id]?.avgSpeedKmh?.let { v -> "$v ${unitSystem.speedUnit}" } ?: "—" },
            bestIndices(trips.map { displayMetrics[it.id]?.avgSpeedKmh?.toDouble() }, false)),
        MetricRow(
            if (socSource == SocSource.PANEL) stringResource(R.string.soc_start_end_panel) else stringResource(R.string.soc_start_end_bms),
            if (socSource == SocSource.PANEL)
                trips.map { "${it.startSocPanel.toInt()}% → ${it.endSocPanel?.toInt() ?: "—"}%" }
            else
                trips.map { "${it.startSoc.toInt()}% → ${it.endSoc?.toInt() ?: "—"}%" },
            trips.map { false }),
        MetricRow(stringResource(R.string.regen_recovered_label),
            trips.map { statsById[it.id]?.totalRegenEnergy?.let { v -> "%.2f kWh".format(v) } ?: "—" },
            bestIndices(trips.map { statsById[it.id]?.totalRegenEnergy }, false)),
        MetricRow(stringResource(R.string.stat_regen_eff),
            trips.map { displayMetrics[it.id]?.regenEfficiencyPct?.let { v -> "%.1f%%".format(v) } ?: "—" },
            bestIndices(trips.map { displayMetrics[it.id]?.regenEfficiencyPct }, false)),
        MetricRow(stringResource(R.string.trip_score_label),
            trips.map { displayMetrics[it.id]?.tripScore?.toString() ?: "—" },
            bestIndices(trips.map { displayMetrics[it.id]?.tripScore?.toDouble() }, false))
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Column headers
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Spacer(Modifier.weight(1.2f))
            trips.forEachIndexed { i, trip ->
                Text(
                    text       = tripShortLabel(trip),
                    style      = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign  = TextAlign.Center,
                    color      = if (i in visibleTrips) tripColor(i)
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    modifier   = Modifier.weight(1f)
                )
            }
        }

        rows.forEachIndexed { rowIdx, row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (rowIdx % 2 == 0)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        else
                            MaterialTheme.colorScheme.surface
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    row.label,
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1.2f)
                )
                row.values.forEachIndexed { i, v ->
                    val hidden   = i !in visibleTrips
                    val isWinner = row.winners.getOrElse(i) { false }
                    Box(
                        modifier         = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text       = if (hidden) "—" else v,
                            style      = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isWinner) FontWeight.Bold else FontWeight.Normal,
                            color      = when {
                                hidden   -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                                isWinner -> tripColor(i)
                                else     -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

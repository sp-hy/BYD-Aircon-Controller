package com.byd.tripstats.ui.components.routeanalysis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.byd.tripstats.R
import com.byd.tripstats.data.local.entity.TripDataPointEntity
import com.byd.tripstats.data.preferences.SocSource
import com.byd.tripstats.ui.theme.AccelerationOrange
import com.byd.tripstats.ui.theme.RegenGreen
import kotlin.math.abs

@Composable
internal fun RouteSegmentsCard(
    dataPoints: List<TripDataPointEntity>,
    useImperial: Boolean = false,
    socSource: SocSource = SocSource.PANEL
) {
    val segmentSize = (dataPoints.size / 5).coerceAtLeast(1)
    val segments    = dataPoints.chunked(segmentSize).take(5)
    val speedFactor = if (useImperial) 0.621371 else 1.0
    val speedUnit   = if (useImperial) "mph" else "km/h"

    Card(
        modifier = Modifier.fillMaxWidth().then(cardBorder),
        colors = cardColors
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.route_segments_label),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            segments.forEachIndexed { index, segment ->
                val avgSpeed  = segment.map { it.speed }.average()
                val avgPower  = segment.map { it.power }.average()
                val socOf: (TripDataPointEntity) -> Double = { p ->
                    if (socSource == SocSource.PANEL && p.socPanel > 0) p.socPanel.toDouble() else p.soc
                }
                val socChange = socOf(segment.first()) - socOf(segment.last())
                val startTime = fmt(segment.first().timestamp)
                val endTime   = fmt(segment.last().timestamp)

                SegmentItem(
                    segmentNumber = index + 1,
                    timeRange     = "$startTime – $endTime",
                    avgSpeed      = (avgSpeed * speedFactor).toInt(),
                    speedUnit     = speedUnit,
                    avgPower      = avgPower.toInt(),
                    socChange     = socChange,
                    socLabel      = if (socSource == SocSource.PANEL) stringResource(R.string.stat_soc_panel) else stringResource(R.string.stat_soc_bms)
                )

                if (index < segments.size - 1) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun SegmentItem(
    segmentNumber: Int,
    timeRange: String,
    avgSpeed: Int,
    speedUnit: String = "km/h",
    avgPower: Int,
    socChange: Double,
    socLabel: String = "SoC (BMS)"
) {
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
        Column {
            Text(
                text = stringResource(R.string.segment_number_label, segmentNumber),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = timeRange,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(text = "$avgSpeed $speedUnit", style = MaterialTheme.typography.bodySmall)
            Text(
                text = "${abs(avgPower)} kW",
                style = MaterialTheme.typography.bodySmall,
                color = if (avgPower < 0) RegenGreen else AccelerationOrange
            )
            Text(
                text = "${String.format("%.1f", abs(socChange))}% $socLabel",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

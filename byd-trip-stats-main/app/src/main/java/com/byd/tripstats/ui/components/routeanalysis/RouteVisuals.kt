package com.byd.tripstats.ui.components.routeanalysis

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.FlagCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import com.byd.tripstats.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.byd.tripstats.data.local.entity.TripDataPointEntity
import com.byd.tripstats.ui.theme.AccelerationOrange
import com.byd.tripstats.ui.theme.BydErrorRed
import com.byd.tripstats.ui.theme.RegenGreen
import kotlin.math.abs

@Composable
internal fun EnergyHeatmapCard(dataPoints: List<TripDataPointEntity>) {
    data class EnergySegment(val startTs: Long, val endTs: Long, val energyKwh: Double)

    val segments = dataPoints.chunked(10).map { chunk ->
        var energy = 0.0
        for (i in 1 until chunk.size) {
            val dtSeconds = (chunk[i].timestamp - chunk[i - 1].timestamp) / 1000.0
            energy += abs(chunk[i].power) * dtSeconds / 3600.0
        }
        EnergySegment(
            startTs   = chunk.first().timestamp,
            endTs     = chunk.last().timestamp,
            energyKwh = energy
        )
    }.sortedByDescending { it.energyKwh }.take(5)

    Card(
        modifier = Modifier.fillMaxWidth().then(cardBorder),
        colors = cardColors
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.route_energy_hotspots),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.route_energy_hotspots_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            segments.forEachIndexed { index, seg ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${fmt(seg.startTs)} – ${fmt(seg.endTs)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "${String.format("%.3f", seg.energyKwh)} kWh",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = AccelerationOrange
                    )
                }
                if (index < segments.size - 1) {
                    HorizontalDivider(
                        modifier  = Modifier.padding(vertical = 2.dp),
                        thickness = 0.5.dp,
                        color     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                    )
                }
            }
        }
    }
}

@Composable
internal fun TripTimelineCard(dataPoints: List<TripDataPointEntity>) {
    val strTripStarted     = stringResource(R.string.route_trip_started)
    val strHardAcceleration = stringResource(R.string.route_hard_acceleration)
    val strHardBraking     = stringResource(R.string.route_hard_braking)
    val strTripEnded       = stringResource(R.string.route_trip_ended)

    val events = mutableListOf<TimelineEvent>()

    events.add(TimelineEvent(fmt(dataPoints.first().timestamp), strTripStarted, Icons.Filled.FlagCircle, RegenGreen))

    val window = 5
    var lastEventTs = dataPoints.first().timestamp

    for (i in window until dataPoints.size) {
        val curr = dataPoints[i]
        val gapSeconds = (curr.timestamp - lastEventTs) / 1000.0
        if (gapSeconds < 10.0) continue

        val avgBefore = dataPoints.subList(i - window, i).map { it.power }.average()
        val avgAfter  = dataPoints.subList(i, (i + window).coerceAtMost(dataPoints.size)).map { it.power }.average()
        val delta     = avgAfter - avgBefore

        if (abs(delta) > 30) {
            events.add(TimelineEvent(
                time  = fmt(curr.timestamp),
                title = if (delta > 0) strHardAcceleration else strHardBraking,
                icon  = if (delta > 0) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                color = if (delta > 0) AccelerationOrange else RegenGreen
            ))
            lastEventTs = curr.timestamp
        }
    }

    events.add(TimelineEvent(fmt(dataPoints.last().timestamp), strTripEnded, Icons.Filled.LocationOn, BydErrorRed))
    val visibleEvents = sampleTimelineEvents(events, maxVisible = 15)

    Card(
        modifier = Modifier.fillMaxWidth().then(cardBorder),
        colors = cardColors
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.route_trip_timeline),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            visibleEvents.forEachIndexed { index, event ->
                TimelineEventItem(event)
                if (index < (visibleEvents.size - 1)) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun TimelineEventItem(event: TimelineEvent) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = event.icon, contentDescription = event.title, tint = event.color, modifier = Modifier.size(24.dp))
        Column {
            Text(text = event.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(text = event.time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private data class TimelineEvent(
    val time:  String,
    val title: String,
    val icon:  ImageVector,
    val color: Color
)

private fun sampleTimelineEvents(events: List<TimelineEvent>, maxVisible: Int): List<TimelineEvent> {
    if (events.size <= maxVisible) return events
    val lastIndex = events.lastIndex
    val sampledIndices = (0 until maxVisible).map { slot ->
        ((slot.toDouble() / (maxVisible - 1)) * lastIndex).toInt()
    }.distinct().sorted()
    val sampled = sampledIndices.map { events[it] }.toMutableList()
    if (sampled.firstOrNull() != events.first()) sampled.add(0, events.first())
    if (sampled.lastOrNull() != events.last()) sampled.add(events.last())
    return sampled.distinct()
}

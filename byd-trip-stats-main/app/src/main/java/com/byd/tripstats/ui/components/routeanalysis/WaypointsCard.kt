package com.byd.tripstats.ui.components.routeanalysis

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlagCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.byd.tripstats.R
import com.byd.tripstats.data.local.entity.TripDataPointEntity
import com.byd.tripstats.data.preferences.SocSource
import com.byd.tripstats.ui.theme.BydErrorRed
import com.byd.tripstats.ui.theme.RegenGreen

@Composable
internal fun WaypointsCard(
    dataPoints: List<TripDataPointEntity>,
    socSource: SocSource = SocSource.PANEL
) {
    val startPoint = dataPoints.first()
    val endPoint   = dataPoints.last()

    Card(
        modifier = Modifier.fillMaxWidth().then(cardBorder),
        colors = cardColors
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.waypoints_label),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            WaypointItem(
                icon  = Icons.Filled.FlagCircle,
                label = stringResource(R.string.waypoint_start),
                time  = fmt(startPoint.timestamp),
                soc   = "${"%.1f".format(
                    if (socSource == SocSource.PANEL && startPoint.socPanel > 0) startPoint.socPanel.toDouble()
                    else startPoint.soc
                )}%",
                color = RegenGreen
            )
            Spacer(modifier = Modifier.height(8.dp))
            WaypointItem(
                icon  = Icons.Filled.LocationOn,
                label = stringResource(R.string.waypoint_end),
                time  = fmt(endPoint.timestamp),
                soc   = "${"%.1f".format(
                    if (socSource == SocSource.PANEL && endPoint.socPanel > 0) endPoint.socPanel.toDouble()
                    else endPoint.soc
                )}%",
                color = BydErrorRed
            )
        }
    }
}

@Composable
private fun WaypointItem(
    icon: ImageVector,
    label: String,
    time: String,
    soc: String,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = color, modifier = Modifier.size(32.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(text = time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = stringResource(R.string.soc_at_point, soc), style = MaterialTheme.typography.bodySmall)
        }
    }
}

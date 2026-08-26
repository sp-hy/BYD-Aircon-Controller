package com.byd.tripstats.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.byd.tripstats.data.local.entity.TripDataPointEntity
import com.byd.tripstats.data.local.entity.TripEntity
import com.byd.tripstats.data.preferences.SocSource
import com.byd.tripstats.ui.components.routeanalysis.*

@Composable
fun RouteAnalysisTab(
    trip       : TripEntity? = null,
    dataPoints : List<TripDataPointEntity>,
    useImperial: Boolean = false,
    socSource  : SocSource = SocSource.PANEL,
    modifier   : Modifier = Modifier
) {
    if (dataPoints.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No route data available",
                style = MaterialTheme.typography.titleMedium
            )
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ModeInsightsCard(dataPoints = dataPoints, trip = trip, useImperial = useImperial)
        WaypointsCard(dataPoints, socSource)
        RouteSegmentsCard(dataPoints, useImperial, socSource)
        EnergyHeatmapCard(dataPoints)
        TripTimelineCard(dataPoints)
    }
}

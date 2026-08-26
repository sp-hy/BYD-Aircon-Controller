package com.byd.tripstats.ui.screens.tripcompare

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import com.byd.tripstats.R
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.byd.tripstats.data.local.entity.TripEntity
import com.byd.tripstats.data.preferences.UnitSystem
import com.byd.tripstats.data.preferences.consumptionUnit
import com.byd.tripstats.data.preferences.convertDistance
import com.byd.tripstats.data.preferences.convertEfficiency
import com.byd.tripstats.data.preferences.distanceUnit
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline

@Composable
internal fun CompareRoutesTab(
    trips       : List<TripEntity>,
    compareStats: List<com.byd.tripstats.data.local.entity.TripStatsEntity>,
    visibleTrips: Set<Int>,
    unitSystem  : UnitSystem = UnitSystem.METRIC
) {
    val statsById    = compareStats.associateBy { it.tripId }
    val routesByTrip = trips.mapIndexedNotNull { i, trip ->
        val route = statsById[trip.id]?.compressedRoute
        if (route.isNullOrEmpty()) null else Triple(i, trip, route)
    }

    // Map fills the tab — no outer scroll wrapper so the map gets all gestures.
    // The sheet can still be dismissed by dragging the handle above the tabs.
    Box(modifier = Modifier.fillMaxSize().clipToBounds()) {

        if (routesByTrip.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.chart_no_route_data),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Box
        }

        val visibleRoutes = routesByTrip.filter { (i, _, _) -> i in visibleTrips }
        val allGeoPoints  = visibleRoutes.flatMap { (_, _, route) ->
            route.map { GeoPoint(it.lat, it.lon) }
        }

        AndroidView(
            factory = { ctx ->
                Configuration.getInstance()
                    .load(ctx, ctx.getSharedPreferences("osm_prefs", 0))
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(13.0)
                    // Hide built-in +/- buttons — pinch-to-zoom is sufficient
                    // and frees the bottom of the map for the legend card.
                    zoomController.setVisibility(
                        org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER
                    )
                    // Tell every parent scroll/sheet not to steal touches while
                    // a finger is down on the map. Return false so MapView still
                    // receives and handles the event itself.
                    setOnTouchListener { v, event ->
                        when (event.action) {
                            android.view.MotionEvent.ACTION_DOWN,
                            android.view.MotionEvent.ACTION_MOVE ->
                                v.parent?.requestDisallowInterceptTouchEvent(true)
                            android.view.MotionEvent.ACTION_UP,
                            android.view.MotionEvent.ACTION_CANCEL -> {
                                v.parent?.requestDisallowInterceptTouchEvent(false)
                                v.performClick()
                            }
                        }
                        false
                    }
                }
            },
            update = { mapView ->
                mapView.overlays.clear()
                routesByTrip.forEach { (colorIndex, _, route) ->
                    if (colorIndex !in visibleTrips || route.size < 2) return@forEach
                    val polyline = Polyline().apply {
                        outlinePaint.color       = tripColorArgb(colorIndex)
                        outlinePaint.strokeWidth = 8f
                        outlinePaint.isAntiAlias = true
                        setPoints(route.map { GeoPoint(it.lat, it.lon) })
                    }
                    mapView.overlays.add(polyline)
                }
                if (allGeoPoints.size >= 2) {
                    val box = BoundingBox.fromGeoPoints(allGeoPoints)
                    mapView.post {
                        mapView.zoomToBoundingBox(box.increaseByScale(1.15f), true)
                    }
                }
                mapView.invalidate()
            },
            modifier = Modifier.fillMaxSize()
        )

        // ── Bottom-left legend overlay ────────────────────────────────────────
        // Consolidates: color swatch + date label + distance + consumption
        // Semi-transparent so the map shows through faintly.
        Card(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 10.dp, bottom = 24.dp),
            shape     = RoundedCornerShape(8.dp),
            colors    = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.70f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border    = androidx.compose.foundation.BorderStroke(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                routesByTrip.forEach { (colorIndex, trip, _) ->
                    val hidden = colorIndex !in visibleTrips
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Color swatch
                        Canvas(modifier = Modifier.size(24.dp, 4.dp)) {
                            drawLine(
                                color = if (hidden) tripColor(colorIndex).copy(alpha = 0.25f)
                                else tripColor(colorIndex),
                                start = Offset(0f, size.height / 2),
                                end   = Offset(size.width, size.height / 2),
                                strokeWidth = 4f,
                                cap   = StrokeCap.Round
                            )
                        }
                        // Date label
                        Text(
                            tripShortLabel(trip),
                            style      = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color      = if (hidden) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            else tripColor(colorIndex)
                        )
                        // Distance
                        Text(
                            "%.1f ${unitSystem.distanceUnit}".format(unitSystem.convertDistance(trip.distance ?: 0.0)),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (hidden) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            else MaterialTheme.colorScheme.onSurface
                        )
                        // Efficiency
                        Text(
                            trip.efficiency?.let { "%.1f ${unitSystem.consumptionUnit}".format(unitSystem.convertEfficiency(it)) } ?: "—",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (hidden) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

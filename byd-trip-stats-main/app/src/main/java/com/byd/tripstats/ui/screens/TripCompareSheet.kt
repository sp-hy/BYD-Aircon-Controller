package com.byd.tripstats.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.byd.tripstats.R
import com.byd.tripstats.data.local.entity.TripEntity
import com.byd.tripstats.data.preferences.SocSource
import com.byd.tripstats.data.preferences.UnitSystem
import com.byd.tripstats.ui.viewmodel.DashboardViewModel
import com.byd.tripstats.ui.screens.tripcompare.*

// ── Entry point ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripCompareSheet(
    trips    : List<TripEntity>,
    viewModel: DashboardViewModel,
    onDismiss: () -> Unit
) {
    val sheetState     = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val compareData    by viewModel.compareDataPoints.collectAsState()
    val displayMetrics by viewModel.tripDisplayMetrics.collectAsState()
    val compareStats    = remember(trips) { viewModel.getCompareStats(trips.map { it.id }) }
    val unitSystem     by viewModel.unitSystem.collectAsState()
    val socSource      by viewModel.socSource.collectAsState()

    var selectedTab  by remember { mutableIntStateOf(0) }
    val tabs = listOf("Summary", "Charts", "Routes")

    // Eye toggle — indices of currently visible trips (all shown by default)
    var visibleTrips by remember { mutableStateOf(trips.indices.toSet()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = MaterialTheme.colorScheme.surface,
        modifier         = Modifier.fillMaxHeight(0.92f)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Header ────────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.CompareArrows, null,
                    tint     = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.comparing_trips_label, trips.size),
                    style      = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            // ── Trip legend with eye toggles ──────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                trips.forEachIndexed { i, trip ->
                    val visible = i in visibleTrips
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            visibleTrips = if (visible)
                                visibleTrips - i
                            else
                                visibleTrips + i
                        }
                    ) {
                        Canvas(modifier = Modifier.size(20.dp, 3.dp)) {
                            drawLine(
                                color       = if (visible) tripColor(i)
                                else tripColor(i).copy(alpha = 0.25f),
                                start       = Offset(0f, size.height / 2),
                                end         = Offset(size.width, size.height / 2),
                                strokeWidth = 3f
                            )
                        }
                        Spacer(Modifier.width(5.dp))
                        Text(
                            tripShortLabel(trip),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (visible) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector        = if (visible) Icons.Filled.Visibility
                            else         Icons.Filled.VisibilityOff,
                            contentDescription = if (visible) stringResource(R.string.hide_trip_action) else stringResource(R.string.show_trip_action),
                            tint     = if (visible) tripColor(i)
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // ── Tabs ──────────────────────────────────────────────────────────
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { i, title ->
                    Tab(
                        selected = selectedTab == i,
                        onClick  = { selectedTab = i },
                        text = {
                            Text(
                                title,
                                fontWeight = if (selectedTab == i) FontWeight.Bold
                                else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            // ── Tab content ───────────────────────────────────────────────────
            // weight(1f)   — allocates exactly the remaining height after header/tabs
            // clipToBounds — hard clips any child that escapes during sheet drag
            //                animation (sheet remeasures with unbounded constraints;
            //                weight alone is not sufficient during that pass).
            Box(modifier = Modifier.weight(1f).clipToBounds()) {
                when (selectedTab) {
                    0 -> CompareSummaryTab(trips, displayMetrics, compareStats, visibleTrips, unitSystem, socSource)
                    1 -> CompareChartsTab(trips, compareData, visibleTrips, unitSystem, socSource)
                    2 -> CompareRoutesTab(trips, compareStats, visibleTrips, unitSystem)
                }
            }
        }
    }
}

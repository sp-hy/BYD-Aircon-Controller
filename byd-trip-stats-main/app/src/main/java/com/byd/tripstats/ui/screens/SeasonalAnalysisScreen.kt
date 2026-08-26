package com.byd.tripstats.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.tripstats.data.preferences.UnitSystem
import com.byd.tripstats.data.preferences.consumptionUnit
import com.byd.tripstats.data.preferences.convertEfficiency
import androidx.compose.ui.res.stringResource
import com.byd.tripstats.R
import com.byd.tripstats.ui.components.BrandNavigationBar
import com.byd.tripstats.ui.theme.*
import com.byd.tripstats.ui.viewmodel.DashboardViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeasonalAnalysisScreen(
    viewModel      : DashboardViewModel,
    onNavigateBack : () -> Unit
) {
    val seasonalStats  by viewModel.seasonalStats.collectAsState()
    val selectedCar    by viewModel.selectedCarConfig.collectAsState()
    val reference      = selectedCar?.referenceConsumptionKwhPer100km
    val unitSystem     by viewModel.unitSystem.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(R.string.seasonal_analysis_title), fontSize = 18.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { onNavigateBack() }
                        )
                        VerticalDivider(
                            modifier = Modifier.height(14.dp),
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(stringResource(R.string.winter_vs_summer_subtitle),
                            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                  BrandNavigationBar {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back),
                            modifier = Modifier.size(32.dp))
                    }
                  }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        if (seasonalStats.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("🌍", fontSize = 52.sp)
                    Text(stringResource(R.string.no_seasonal_data),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.complete_seasons_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // ── Bar chart ─────────────────────────────────────────────────────
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .height(260.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(Modifier.fillMaxSize().padding(12.dp)) {
                    Text(stringResource(R.string.avg_consumption_by_season),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)
                    if (reference != null) {
                        Text(stringResource(R.string.reference_line_label,
                            "%.1f".format(unitSystem.convertEfficiency(reference)), unitSystem.consumptionUnit),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(6.dp))
                    SeasonalBarChart(
                        stats      = seasonalStats,
                        reference  = reference,
                        unitSystem = unitSystem,
                        modifier   = Modifier.fillMaxSize()
                    )
                }
            }

            // ── Season cards ──────────────────────────────────────────────────
            seasonalStats.forEach { stat ->
                SeasonCard(stat = stat, reference = reference, unitSystem = unitSystem)
            }

            // ── Insight card ──────────────────────────────────────────────────
            val winter = seasonalStats.find { it.season == DashboardViewModel.Season.WINTER }
            val summer = seasonalStats.find { it.season == DashboardViewModel.Season.SUMMER }
            if (winter != null && summer != null) {
                val delta    = unitSystem.convertEfficiency(winter.avgConsumption - summer.avgConsumption)
                val deltaPct = (delta / unitSystem.convertEfficiency(summer.avgConsumption) * 100).roundToInt()
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Filled.Lightbulb, null,
                            tint = AccelerationOrange, modifier = Modifier.size(22.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.winter_penalty_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold)
                            Text(
                                stringResource(R.string.winter_penalty_desc,
                                    "%.1f".format(delta), unitSystem.consumptionUnit, deltaPct),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ── Bar chart ─────────────────────────────────────────────────────────────────

@Composable
private fun SeasonalBarChart(
    stats      : List<DashboardViewModel.SeasonStats>,
    reference  : Double?,
    unitSystem : UnitSystem = UnitSystem.METRIC,
    modifier   : Modifier = Modifier
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f)
    val effFactor = if (unitSystem == UnitSystem.IMPERIAL) 1.0 / 0.621371 else 1.0

    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        val padL = 48f; val padR = 12f; val padT = 8f; val padB = 36f
        val chartW = w - padL - padR; val chartH = h - padT - padB

        val maxVal  = (stats.maxOf { it.avgConsumption * effFactor } * 1.15).coerceAtLeast(
            reference?.times(effFactor * 1.15) ?: 20.0)
        val minVal  = 0.0
        val valRange = maxVal - minVal

        fun yOf(v: Double) = (padT + chartH * (1.0 - (v - minVal) / valRange)).toFloat()
        val nc = drawContext.canvas.nativeCanvas

        // Y grid lines (every 5 kWh/100km)
        val yStep = if (maxVal <= 30) 5.0 else 10.0
        var yTick = yStep
        while (yTick <= maxVal) {
            val y = yOf(yTick)
            drawLine(gridColor, Offset(padL, y), Offset(w - padR, y), 1f)
            nc.drawText("%.0f".format(yTick), padL - 6f, y + 8f,
                android.graphics.Paint().apply {
                    color = textColor.copy(alpha = 0.6f).toArgb()
                    textSize = 20f
                    textAlign = android.graphics.Paint.Align.RIGHT
                    isAntiAlias = true
                })
            yTick += yStep
        }

        // Bars
        val barWidth  = (chartW / (stats.size * 1.8f))
        val groupStep = chartW / stats.size

        stats.forEachIndexed { i, stat ->
            val barColor = seasonColor(stat.season)
            val x = padL + i * groupStep + groupStep / 2f - barWidth / 2f
            val barTop = yOf(stat.avgConsumption * effFactor)
            val barBot = yOf(0.0)

            drawRoundRect(
                color     = barColor,
                topLeft   = Offset(x, barTop),
                size      = Size(barWidth, barBot - barTop),
                cornerRadius = CornerRadius(6f)
            )

            // Value label above bar
            nc.drawText(
                "%.1f".format(stat.avgConsumption * effFactor),
                x + barWidth / 2f,
                barTop - 6f,
                android.graphics.Paint().apply {
                    color = textColor.copy(alpha = 0.85f).toArgb()
                    textSize = 21f
                    textAlign = android.graphics.Paint.Align.CENTER
                    isFakeBoldText = true
                    isAntiAlias = true
                }
            )

            // Season label below bar
            nc.drawText(
                stat.season.emoji,
                x + barWidth / 2f,
                h - 16f,
                android.graphics.Paint().apply {
                    textSize = 26f
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                }
            )
        }

        // Reference line
        if (reference != null && reference * effFactor < maxVal) {
            val refY = yOf(reference * effFactor)
            drawLine(
                color       = AccelerationOrange.copy(alpha = 0.7f),
                start       = Offset(padL, refY),
                end         = Offset(w - padR, refY),
                strokeWidth = 2f,
                pathEffect  = androidx.compose.ui.graphics.PathEffect
                    .dashPathEffect(floatArrayOf(10f, 6f))
            )
        }
    }
}

// ── Season card ───────────────────────────────────────────────────────────────

@Composable
private fun SeasonCard(
    stat       : DashboardViewModel.SeasonStats,
    reference  : Double?,
    unitSystem : UnitSystem = UnitSystem.METRIC
) {
    val barColor = seasonColor(stat.season)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header row
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stat.season.emoji, fontSize = 24.sp)
                    Column {
                        Text(stat.season.label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold)
                        Text(
                            if (stat.tripCount == 1) stringResource(R.string.stat_one_trip)
                            else stringResource(R.string.stat_n_trips, stat.tripCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${"%.1f".format(unitSystem.convertEfficiency(stat.avgConsumption))} ${unitSystem.consumptionUnit}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = barColor)
                    if (reference != null) {
                        val diff = unitSystem.convertEfficiency(stat.avgConsumption - reference)
                        val sign = if (diff >= 0) "+" else ""
                        Text(stringResource(R.string.season_vs_ref, "${sign}${"%.1f".format(diff)}"),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (diff <= 0) RegenGreen
                                    else MaterialTheme.colorScheme.error)
                    }
                }
            }

            // Consumption bar (relative to max among seasons)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp))
            ) {
                // Filled portion
                Box(
                    Modifier
                        .fillMaxHeight()
                        .background(barColor, RoundedCornerShape(3.dp))
                )
            }

            // Stats row
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SeasonStatChip("🛣️", stringResource(R.string.season_stat_km_total, "%.0f".format(stat.totalDistanceKm)))
                SeasonStatChip("⚡", stringResource(R.string.season_stat_kwh_used, "%.1f".format(stat.totalKwh)))
                SeasonStatChip("🌡️", stringResource(R.string.season_stat_avg_temp, "%.0f".format(stat.avgTempC)))
            }
        }
    }
}

@Composable
private fun SeasonStatChip(icon: String, label: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 12.sp)
        Text(label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun seasonColor(season: DashboardViewModel.Season): Color = when (season) {
    DashboardViewModel.Season.SPRING -> Color(0xFF66BB6A)
    DashboardViewModel.Season.SUMMER -> Color(0xFFFFCA28)
    DashboardViewModel.Season.AUTUMN -> Color(0xFFFF7043)
    DashboardViewModel.Season.WINTER -> Color(0xFF42A5F5)
}
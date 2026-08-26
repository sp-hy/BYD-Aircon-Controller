package com.byd.tripstats.ui.screens.tripcompare

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import com.byd.tripstats.R
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.byd.tripstats.data.local.entity.TripEntity
import com.byd.tripstats.data.preferences.SocSource
import com.byd.tripstats.data.preferences.UnitSystem
import com.byd.tripstats.data.preferences.consumptionUnit
import com.byd.tripstats.data.preferences.speedUnit

private enum class CompareChartType(
    val label     : String,
    val yAxisLabel: String,
    val yFloor    : Double? = null
) {
    SPEED("Speed", "km/h", yFloor = 0.0),
    POWER("Power", "kW"),
    ENERGY_CONSUMPTION("Consumption", "kWh/100", yFloor = 0.0),
    SOC("SoC", "%", yFloor = 0.0),
    ELEVATION("Elevation", "m")
}

@Composable
internal fun CompareChartsTab(
    trips       : List<TripEntity>,
    compareData : Map<Long, List<com.byd.tripstats.data.local.entity.TripDataPointEntity>>,
    visibleTrips: Set<Int>,
    unitSystem  : UnitSystem = UnitSystem.METRIC,
    socSource   : SocSource  = SocSource.PANEL,
) {
    if (trips.any { it.id !in compareData }) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.loading_chart_data), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    val normalised = trips.map { trip ->
        normaliseTripData(compareData[trip.id] ?: emptyList(), socSource = socSource)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CompareChartType.entries.forEach { chartType ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp)),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                    Text(chartType.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
                    OverlaidLineChart(
                        series = normalised.mapIndexed { i, pts ->
                            Triple(pts, tripColor(i), i in visibleTrips)
                        },
                        yAxisLabel    = when (chartType) {
                            CompareChartType.SPEED              -> unitSystem.speedUnit
                            CompareChartType.ENERGY_CONSUMPTION -> unitSystem.consumptionUnit
                            else                                -> chartType.yAxisLabel
                        },
                        yFloor        = chartType.yFloor,
                        valueSelector = { pt ->
                            when (chartType) {
                                CompareChartType.SPEED              -> pt.avgSpeed
                                CompareChartType.POWER              -> pt.avgPower
                                CompareChartType.ENERGY_CONSUMPTION -> pt.avgConsumption
                                CompareChartType.SOC                -> pt.avgSoc
                                CompareChartType.ELEVATION          -> pt.avgElevation
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ── Multi-series canvas line chart ────────────────────────────────────────────

@Composable
private fun OverlaidLineChart(
    series       : List<Triple<List<NormalisedPoint>, Color, Boolean>>,
    yAxisLabel   : String,
    yFloor       : Double? = null,
    valueSelector: (NormalisedPoint) -> Double,
    modifier     : Modifier = Modifier
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f)
    val axisColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.30f)

    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        val padL = 68f; val padR = 12f; val padT = 8f; val padB = 32f
        val chartW = w - padL - padR; val chartH = h - padT - padB

        // Y range from ALL series so the axis is stable when hiding/showing trips
        val allVals = series.flatMap { (pts, _, _) -> pts.map { valueSelector(it) } }
        if (allVals.isEmpty()) return@Canvas
        val rawMin = allVals.min(); val rawMax = allVals.max()
        val range  = (rawMax - rawMin).coerceAtLeast(1.0)
        val yStep  = niceStepCompare(range)
        val yMinRaw = (rawMin / yStep).toInt() * yStep - yStep
        val yMin = if (yFloor != null) maxOf(yMinRaw, yFloor) else yMinRaw
        val yMax = (rawMax / yStep).toInt() * yStep + yStep

        fun xOf(pct: Double) = padL + (pct / 100.0 * chartW).toFloat()
        fun yOf(v: Double)   = (padT + chartH * (1.0 - (v - yMin) / (yMax - yMin))).toFloat()

        val nc = drawContext.canvas.nativeCanvas
        val labelPaint = android.graphics.Paint().apply {
            color = textColor.copy(alpha = 0.65f).toArgb(); textSize = 20f; isAntiAlias = true
        }

        var yTick = yMin
        while (yTick <= yMax + 0.01) {
            val y = yOf(yTick)
            drawLine(gridColor, Offset(padL, y), Offset(w - padR, y), 1f)
            labelPaint.textAlign = android.graphics.Paint.Align.RIGHT
            nc.drawText("%.0f".format(yTick), padL - 4f, y + 7f, labelPaint)
            yTick += yStep
        }

        val yAxisPaint = android.graphics.Paint().apply {
            color = textColor.copy(alpha = 0.50f).toArgb(); textSize = 18f
            textAlign = android.graphics.Paint.Align.CENTER; isAntiAlias = true
        }
        nc.save()
        nc.rotate(-90f, 14f, padT + chartH / 2f)
        nc.drawText(yAxisLabel, 14f, padT + chartH / 2f, yAxisPaint)
        nc.restore()

        drawLine(axisColor, Offset(padL, padT + chartH), Offset(w - padR, padT + chartH), 1.5f)
        val xLabelPaint = android.graphics.Paint().apply {
            color = textColor.copy(alpha = 0.55f).toArgb(); textSize = 18f
            textAlign = android.graphics.Paint.Align.CENTER; isAntiAlias = true
        }
        listOf(0, 25, 50, 75, 100).forEach { pct ->
            nc.drawText("$pct%", xOf(pct.toDouble()), h - 4f, xLabelPaint)
        }

        series.forEach { (pts, color, visible) ->
            if (!visible || pts.size < 2) return@forEach
            val path = Path().apply {
                moveTo(xOf(pts.first().distPct), yOf(valueSelector(pts.first())))
                pts.drop(1).forEach { pt -> lineTo(xOf(pt.distPct), yOf(valueSelector(pt))) }
            }
            drawPath(path, color.copy(alpha = 0.85f),
                style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

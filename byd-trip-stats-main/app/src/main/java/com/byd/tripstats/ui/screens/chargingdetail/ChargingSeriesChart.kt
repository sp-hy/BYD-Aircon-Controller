package com.byd.tripstats.ui.screens.chargingdetail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.byd.tripstats.R
import com.byd.tripstats.data.preferences.SocSource
import com.byd.tripstats.ui.components.drawCrosshair
import com.byd.tripstats.ui.theme.BatteryBlue

/** One line on a [ChargingSeriesChart]. `selector` returns the y-value in chart units; a value
 *  of `<= 0` means "not reported at this point" and is treated as a gap (the line skips it). */
internal data class ChargingSeriesSpec(
    val label: String,
    val color: Color,
    val width: Float,
    val selector: (ChargingChartPoint) -> Double,
)

/** What the crosshair shows for the nearest point: which y-value to pin the dot on (in the primary
 *  axis' units), the value text line(s), and the accent colour. The chart appends a SoC + elapsed
 *  context line. */
internal class ChargingCrosshairInfo(
    val anchor: Double,
    val lines: List<String>,
    val color: Color,
)

/**
 * Shared line chart for the charging-detail tabs (Power, Voltage, Cell V, Temperature). Plots one or
 * more primary series on the left axis, against **elapsed time or SoC** (toggled in the header). In
 * time mode it also overlays the **SoC** curve on a right axis, so every chart carries the same SoC
 * context.
 *
 * Each series is drawn only where its value is reported (`> 0`): a per-cell range the BMS starts
 * publishing partway through a charge appears as a gap rather than a drop to 0, and the axis scales
 * to the values present. Series are drawn back-to-front so the **first** in the list renders on top
 * (e.g. the temperature average over the min/max band). A single-series chart also gets an area fill.
 */
@Composable
internal fun ChargingSeriesChart(
    dataPoints  : List<ChargingChartPoint>,
    isSynthetic : Boolean,
    title       : String,
    yAxisLabel  : String,
    series      : List<ChargingSeriesSpec>,
    socSource   : SocSource,
    xAxisMode   : ChargingXAxisMode,
    onXAxisModeChange: (ChargingXAxisMode) -> Unit,
    crosshairFor: (ChargingChartPoint) -> ChargingCrosshairInfo?,
) {
    if (dataPoints.size < 2) {
        ChartEmptyState(isSynthetic)
        return
    }

    val textColor = MaterialTheme.colorScheme.onSurface
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
    val axisColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    val socColor  = BatteryBlue
    var touchPos by remember { mutableStateOf<Offset?>(null) }
    val hasPanelSoc = socSource == SocSource.PANEL && dataPoints.any { it.socPanel > 0.0 }
    val socOf: (ChargingChartPoint) -> Double = { p -> if (hasPanelSoc) p.socPanel else p.soc }
    val showSoc = xAxisMode == ChargingXAxisMode.TIME

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Card(
            modifier = Modifier.fillMaxSize()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                    shape = MaterialTheme.shapes.medium
                ),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(top = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    ChargingXAxisToggle(xAxisMode, onXAxisModeChange)
                }

                if (series.size > 1 || showSoc) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (series.size > 1) series.forEach { ChartLegendItem(it.color, it.label) }
                        if (showSoc) ChartLegendItem(socColor, "SoC (%)")
                    }
                }

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                touchPos = down.position
                                drag(down.id) { change -> touchPos = change.position }
                                touchPos = null
                            }
                        }
                ) {
                    val w = size.width
                    val h = size.height
                    val padL = 80f
                    val padR = if (showSoc) 72f else 16f
                    val padT = 16f
                    val padB = 40f
                    val chartW = w - padL - padR
                    val chartH = h - padT - padB

                    // Gap-skip each series to the points where its value is actually reported (> 0).
                    val drawn = series.map { spec -> spec to dataPoints.filter { spec.selector(it) > 0.0 } }
                    val allVals = drawn.flatMap { (spec, pts) -> pts.map { spec.selector(it) } }
                    if (allVals.isEmpty()) return@Canvas
                    val rawMin = allVals.minOrNull() ?: 0.0
                    val rawMax = allVals.maxOrNull()?.coerceAtLeast(rawMin + 0.01) ?: 1.0
                    val yStep  = niceStep(rawMax - rawMin)
                    val yMin   = kotlin.math.floor(rawMin / yStep) * yStep
                    val yMax   = kotlin.math.ceil(rawMax / yStep) * yStep + yStep

                    // SoC right axis (time mode) + x-domain (SoC mode).
                    val socValues = dataPoints.map { socOf(it) }
                    val socMinRaw = socValues.minOrNull() ?: 0.0
                    val socMaxRaw = socValues.maxOrNull()?.coerceAtLeast(socMinRaw + 0.5) ?: 1.0
                    val socStep = niceStep(socMaxRaw - socMinRaw)
                    val socAxisMin = (kotlin.math.floor(socMinRaw / socStep) * socStep).coerceAtLeast(0.0)
                    val socAxisMax = (kotlin.math.ceil(socMaxRaw / socStep) * socStep).coerceAtMost(100.0)
                    val socXMin = socMinRaw
                    val socXMax = socMaxRaw.coerceAtLeast(socXMin + 0.1)

                    val totalMs = dataPoints.last().timestamp - dataPoints.first().timestamp
                    val startMs = dataPoints.first().timestamp

                    fun xOf(p: ChargingChartPoint): Float = when (xAxisMode) {
                        ChargingXAxisMode.TIME ->
                            if (totalMs <= 0L) padL + chartW / 2f
                            else padL + ((p.timestamp - startMs).toDouble() / totalMs.toDouble() * chartW).toFloat()
                        ChargingXAxisMode.SOC ->
                            (padL + (socOf(p) - socXMin) / (socXMax - socXMin) * chartW).toFloat()
                    }
                    fun yOf(v: Double) = (padT + chartH * (1.0 - (v - yMin) / (yMax - yMin))).toFloat()
                    fun yOfSoc(v: Double) = (padT + chartH * (1.0 - (v - socAxisMin) / (socAxisMax - socAxisMin))).toFloat()

                    val nc = drawContext.canvas.nativeCanvas
                    val labelPaint = android.graphics.Paint().apply {
                        color = textColor.copy(alpha = 0.7f).toArgb(); textSize = 22f; isAntiAlias = true
                    }
                    val xLabelPaint = android.graphics.Paint().apply {
                        color = textColor.copy(alpha = 0.7f).toArgb(); textSize = 20f
                        textAlign = android.graphics.Paint.Align.CENTER; isAntiAlias = true
                    }

                    // Left axis grid + labels.
                    var yTick = yMin
                    while (yTick <= yMax + 0.0001) {
                        val y = yOf(yTick)
                        drawLine(gridColor, Offset(padL, y), Offset(w - padR, y), 1f)
                        labelPaint.color = textColor.copy(alpha = 0.7f).toArgb()
                        labelPaint.textAlign = android.graphics.Paint.Align.RIGHT
                        nc.drawText(axisLabel(yTick, yStep), padL - 6f, y + 8f, labelPaint)
                        yTick += yStep
                    }

                    // Right SoC axis labels (time mode only).
                    if (showSoc) {
                        var socTick = socAxisMin
                        while (socTick <= socAxisMax + 0.0001) {
                            val y = yOfSoc(socTick)
                            labelPaint.color = socColor.copy(alpha = 0.85f).toArgb()
                            labelPaint.textAlign = android.graphics.Paint.Align.LEFT
                            nc.drawText(axisLabel(socTick, socStep), w - padR + 10f, y + 8f, labelPaint)
                            socTick += socStep
                        }
                    }

                    val yAxisPaint = android.graphics.Paint().apply {
                        color = textColor.copy(alpha = 0.55f).toArgb(); textSize = 19f
                        textAlign = android.graphics.Paint.Align.CENTER; isAntiAlias = true
                    }
                    nc.save()
                    nc.rotate(-90f, 18f, padT + chartH / 2f)
                    nc.drawText(yAxisLabel, 18f, padT + chartH / 2f, yAxisPaint)
                    nc.restore()

                    if (showSoc) {
                        val rightAxisPaint = android.graphics.Paint().apply {
                            color = socColor.copy(alpha = 0.9f).toArgb(); textSize = 19f
                            textAlign = android.graphics.Paint.Align.CENTER; isAntiAlias = true
                        }
                        nc.save()
                        nc.rotate(90f, w - 18f, padT + chartH / 2f)
                        nc.drawText("SoC (%)", w - 18f, padT + chartH / 2f, rightAxisPaint)
                        nc.restore()
                    }

                    drawLine(axisColor, Offset(padL, padT + chartH), Offset(w - padR, padT + chartH), 1.5f)

                    // X labels — de-duplicated by minimum pixel spacing, mode-aware.
                    val labelStep = ((dataPoints.size - 1) / 6).coerceAtLeast(1)
                    var lastLabelX = -90f
                    dataPoints.forEachIndexed { i, p ->
                        val consider = i % labelStep == 0 || i == dataPoints.lastIndex
                        val x = xOf(p)
                        if (consider && x - lastLabelX >= 72f) {
                            val label = when (xAxisMode) {
                                ChargingXAxisMode.TIME -> {
                                    val mins = (p.timestamp - startMs) / 60_000L
                                    if (mins >= 60) "+${mins / 60}h${mins % 60}m" else "+${mins}m"
                                }
                                ChargingXAxisMode.SOC -> "%.1f%%".format(socOf(p))
                            }
                            nc.drawText(label, x, h - 8f, xLabelPaint)
                            lastLabelX = x
                        }
                    }

                    // Single-series area fill (preserves the Voltage tab's look).
                    if (drawn.size == 1) {
                        val (spec, pts) = drawn.first()
                        if (pts.size >= 2) {
                            val area = Path().apply {
                                moveTo(xOf(pts.first()), yOf(spec.selector(pts.first())))
                                pts.drop(1).forEach { lineTo(xOf(it), yOf(spec.selector(it))) }
                                lineTo(xOf(pts.last()), padT + chartH)
                                lineTo(xOf(pts.first()), padT + chartH)
                                close()
                            }
                            drawPath(
                                area,
                                Brush.verticalGradient(
                                    listOf(spec.color.copy(alpha = 0.30f), spec.color.copy(alpha = 0f)),
                                    startY = yOf(pts.maxOf { spec.selector(it) }),
                                    endY = padT + chartH
                                )
                            )
                        }
                    }

                    // Draw back-to-front so the first-listed primary series renders on top.
                    drawn.reversed().forEach { (spec, pts) ->
                        if (pts.size >= 2) {
                            val path = Path().apply {
                                moveTo(xOf(pts.first()), yOf(spec.selector(pts.first())))
                                pts.drop(1).forEach { lineTo(xOf(it), yOf(spec.selector(it))) }
                            }
                            drawPath(path, spec.color, style = Stroke(width = spec.width, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    }

                    // SoC overlay (time mode), on the right axis.
                    if (showSoc) {
                        val socPath = Path().apply {
                            moveTo(xOf(dataPoints.first()), yOfSoc(socOf(dataPoints.first())))
                            dataPoints.drop(1).forEach { lineTo(xOf(it), yOfSoc(socOf(it))) }
                        }
                        drawPath(socPath, socColor, style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }

                    touchPos?.let { tp ->
                        if (tp.x in padL..(w - padR)) {
                            val fraction = ((tp.x - padL) / chartW).coerceIn(0f, 1f)
                            val p = when (xAxisMode) {
                                ChargingXAxisMode.TIME -> {
                                    val targetTs = startMs + (fraction * totalMs).toLong()
                                    dataPoints.minByOrNull { kotlin.math.abs(it.timestamp - targetTs) }
                                }
                                ChargingXAxisMode.SOC -> {
                                    val targetSoc = socXMin + fraction * (socXMax - socXMin)
                                    dataPoints.minByOrNull { kotlin.math.abs(socOf(it) - targetSoc) }
                                }
                            }
                            val info = p?.let(crosshairFor)
                            if (p != null && info != null) {
                                val secs = (p.timestamp - startMs) / 1000L
                                val context = "SoC %.1f%% · +%d:%02d".format(socOf(p), secs / 60, secs % 60)
                                val all = info.lines + context
                                drawCrosshair(
                                    cx = xOf(p), cy = yOf(info.anchor),
                                    w = w, padL = padL, padR = padR, padT = padT, chartH = chartH,
                                    line1 = all.getOrElse(0) { "" },
                                    line2 = all.getOrElse(1) { "" },
                                    line3 = all.getOrNull(2),
                                    accentColor = info.color,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ChargingXAxisToggle(mode: ChargingXAxisMode, onChange: (ChargingXAxisMode) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(ChargingXAxisMode.TIME to stringResource(R.string.label_time), ChargingXAxisMode.SOC to "SoC").forEach { (m, label) ->
            val selected = mode == m
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onChange(m) }
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    fontSize = 12.sp,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
                )
            }
        }
    }
}

package com.byd.tripstats.ui.components.heatmaps

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.tripstats.R
import com.byd.tripstats.data.local.entity.TripDataPointEntity
import com.byd.tripstats.ui.components.DriveModeFilter
import com.byd.tripstats.ui.components.RegenModeFilter
import com.byd.tripstats.ui.components.drawCrosshair
import kotlin.math.log10

internal const val KM_TO_MI = 0.621371f

internal fun segmentDistanceKm(a: TripDataPointEntity, b: TripDataPointEntity, dtSec: Double): Float {
    val odometerDeltaKm = (b.odometer - a.odometer).toFloat()
    if (odometerDeltaKm >= 0.001f) return odometerDeltaKm
    if (dtSec <= 0.0) return 0f
    val avgSpeedKmh = ((a.speed + b.speed) / 2.0).coerceAtLeast(0.0)
    return (avgSpeedKmh * (dtSec / 3600.0)).toFloat()
}

@Composable
internal fun <T> ModeFilterRow(
    title: String,
    filters: List<T>,
    selected: T,
    onSelected: (T) -> Unit
) where T : Enum<T> {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filters.forEach { filter ->
                val label = when (filter) {
                    is DriveModeFilter -> filter.label
                    is RegenModeFilter -> filter.label
                    else -> filter.name
                }
                FilterChip(
                    selected = filter == selected,
                    onClick = { onSelected(filter) },
                    label = { Text(label) }
                )
            }
        }
    }
}

@Composable
internal fun Heatmap2D(
    cells      : Array<IntArray>,
    xLabels    : List<String>,
    yLabels    : List<String>,
    xAxisLabel : String,
    yAxisLabel : String,
    xMin       : Float,
    xMax       : Float,
    yMin       : Float,
    yMax       : Float,
    xValueFmt  : (Float) -> String = { "%.1f".format(it) },
    yValueFmt  : (Float) -> String = { "%.1f".format(it) },
    yTickWidth : Float = 50f,
    modifier   : Modifier = Modifier
) {
    val labelArgb    = MaterialTheme.colorScheme.onSurface.toArgb()
    val outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    val accentColor  = MaterialTheme.colorScheme.primary
    var touchPos by remember { mutableStateOf<Offset?>(null) }

    Canvas(modifier = modifier.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown()
            touchPos = down.position
            drag(down.id) { change -> touchPos = change.position }
            touchPos = null
        }
    }) {
        val xBins = cells.size
        if (xBins == 0) return@Canvas
        val yBins = cells[0].size
        if (yBins == 0) return@Canvas

        val yAxisLabelStrip = 22f
        val yTickStrip      = yTickWidth
        val xTickStrip      = 30f
        val xAxisLabelStrip = 26f
        val topPad          = 8f
        val rightPad        = 8f

        val left   = yAxisLabelStrip + yTickStrip
        val right  = size.width - rightPad
        val top    = topPad
        val bottom = size.height - xTickStrip - xAxisLabelStrip

        val gridW  = right - left
        val gridH  = bottom - top
        val cellW  = gridW / xBins
        val cellH  = gridH / yBins

        var maxCount = 1
        for (col in cells) for (v in col) if (v > maxCount) maxCount = v

        for (xi in 0 until xBins) {
            for (yi in 0 until yBins) {
                val count = cells[xi][yi]
                if (count == 0) continue
                val norm = (log10(count + 1f) / log10(maxCount + 1f)).coerceIn(0f, 1f)
                drawRect(
                    color   = heatmapColor(norm),
                    topLeft = Offset(left + xi * cellW + 1f, bottom - (yi + 1) * cellH + 1f),
                    size    = Size(cellW - 2f, cellH - 2f)
                )
            }
        }

        drawRect(
            color   = outlineColor,
            topLeft = Offset(left, top),
            size    = Size(gridW, gridH),
            style   = Stroke(width = 1f)
        )

        drawIntoCanvas { canvas ->
            val nc = canvas.nativeCanvas

            val tickPaint = android.graphics.Paint().apply {
                isAntiAlias = true
                textSize    = 22f
                color       = labelArgb
            }
            val axisPaint = android.graphics.Paint().apply {
                isAntiAlias    = true
                textSize       = 24f
                isFakeBoldText = true
                color          = labelArgb
            }

            tickPaint.textAlign = android.graphics.Paint.Align.CENTER
            xLabels.forEachIndexed { i, lbl ->
                if (lbl.isEmpty()) return@forEachIndexed
                nc.drawText(lbl, left + (i + 0.5f) * cellW, bottom + xTickStrip * 0.85f, tickPaint)
            }

            axisPaint.textAlign = android.graphics.Paint.Align.CENTER
            nc.drawText(xAxisLabel, left + gridW / 2f, size.height - 2f, axisPaint)

            tickPaint.textAlign = android.graphics.Paint.Align.RIGHT
            yLabels.forEachIndexed { i, lbl ->
                if (lbl.isEmpty()) return@forEachIndexed
                val y = bottom - (i + 0.5f) * cellH + tickPaint.textSize / 3f
                nc.drawText(lbl, left - 6f, y, tickPaint)
            }

            val cx = yAxisLabelStrip / 2f
            val cy = top + gridH / 2f
            axisPaint.textAlign = android.graphics.Paint.Align.CENTER
            nc.save()
            nc.rotate(-90f, cx, cy)
            nc.drawText(yAxisLabel, cx, cy + axisPaint.textSize / 3f, axisPaint)
            nc.restore()
        }

        touchPos?.let { tp ->
            if (tp.x in left..right && tp.y in top..bottom) {
                val xi     = ((tp.x - left) / cellW).toInt().coerceIn(0, xBins - 1)
                val yi     = (yBins - 1 - ((tp.y - top) / cellH).toInt()).coerceIn(0, yBins - 1)
                val snapX  = left   + (xi + 0.5f) * cellW
                val snapY  = bottom - (yi + 0.5f) * cellH
                val xStep  = (xMax - xMin) / xBins
                val yStep  = (yMax - yMin) / yBins
                val xLo    = xMin + xi * xStep
                val xHi    = xLo + xStep
                val yLo    = yMin + yi * yStep
                val yHi    = yLo + yStep
                val count  = cells[xi][yi]
                drawCrosshair(
                    cx = snapX, cy = snapY, w = size.width,
                    padL = left, padR = rightPad, padT = top, chartH = gridH,
                    line1 = "$xAxisLabel  ${xValueFmt(xLo)} – ${xValueFmt(xHi)}",
                    line2 = "$yAxisLabel  ${yValueFmt(yLo)} – ${yValueFmt(yHi)}  (${count}×)",
                    accentColor = accentColor
                )
            }
        }
    }
}

@Composable
internal fun HeatmapCard(
    title   : String,
    subtitle: String,
    content : @Composable BoxScope.() -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(340.dp)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                shape = MaterialTheme.shapes.medium
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                content  = content
            )
            Spacer(Modifier.height(6.dp))
            HeatmapLegend()
        }
    }
}

@Composable
internal fun HeatmapLegend() {
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier          = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            stringResource(R.string.heatmap_legend_few),
            style    = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            color    = labelColor
        )
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(10.dp)
        ) {
            val steps = 64
            val w = size.width / steps
            for (i in 0 until steps) {
                val t = i / (steps - 1f)
                drawRect(
                    color   = heatmapColor(t),
                    topLeft = Offset(i * w, 0f),
                    size    = Size(w + 1f, size.height)
                )
            }
        }
        Text(
            stringResource(R.string.heatmap_legend_many),
            style    = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            color    = labelColor
        )
    }
    Text(
        stringResource(R.string.heatmap_legend_density),
        style    = MaterialTheme.typography.labelSmall,
        fontSize = 10.sp,
        color    = labelColor,
        modifier = Modifier.fillMaxWidth()
    )
}

internal fun buildGrid(
    points: List<Pair<Float, Float>>,
    xMin: Float, xMax: Float, xBins: Int,
    yMin: Float, yMax: Float, yBins: Int
): Array<IntArray> {
    val grid   = Array(xBins) { IntArray(yBins) }
    val xRange = xMax - xMin
    val yRange = yMax - yMin
    for ((x, y) in points) {
        if (x < xMin || x > xMax || y < yMin || y > yMax) continue
        val xi = ((x - xMin) / xRange * xBins).toInt().coerceIn(0, xBins - 1)
        val yi = ((y - yMin) / yRange * yBins).toInt().coerceIn(0, yBins - 1)
        grid[xi][yi]++
    }
    return grid
}

internal fun axisLabels(
    min      : Float,
    max      : Float,
    bins     : Int,
    fmt      : String = "%.0f",
    transform: ((Float) -> String)? = null
): List<String> {
    val step = (max - min) / bins
    return List(bins) { i ->
        if (bins <= 8 || i % 2 == 0) {
            val v = min + i * step
            transform?.invoke(v) ?: String.format(fmt, v)
        } else ""
    }
}

internal fun fmtRpm(rpm: Float): String =
    if (rpm >= 1000f) "${"%.1f".format(rpm / 1000f)}K" else "%.0f".format(rpm)

internal fun heatmapColor(t: Float): Color {
    val stops = arrayOf(
        0.000f to Color(0.050f, 0.031f, 0.529f),
        0.250f to Color(0.416f, 0.000f, 0.655f),
        0.500f to Color(0.694f, 0.165f, 0.565f),
        0.750f to Color(0.988f, 0.651f, 0.212f),
        1.000f to Color(0.941f, 0.976f, 0.129f),
    )
    val clamped = t.coerceIn(0f, 1f)
    for (i in 0 until stops.size - 1) {
        val (t0, c0) = stops[i]
        val (t1, c1) = stops[i + 1]
        if (clamped <= t1) return lerp(c0, c1, (clamped - t0) / (t1 - t0))
    }
    return stops.last().second
}

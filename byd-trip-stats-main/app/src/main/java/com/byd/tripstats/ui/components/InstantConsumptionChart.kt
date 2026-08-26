package com.byd.tripstats.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.byd.tripstats.data.local.entity.TripDataPointEntity
import com.byd.tripstats.ui.theme.AccelerationOrange
import com.byd.tripstats.ui.theme.BydElectricAzure
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.byd.tripstats.R

private const val ROLLING_WINDOW = 5   // points for rolling average
private const val CLAMP_MAX = 120.0     // kWh/100km — clip outliers at extremes

@Composable
fun InstantConsumptionChart(
    dataPoints: List<TripDataPointEntity>,
    useImperial: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Only driving points with meaningful speed to avoid divide-by-zero noise
    val drivingPoints = remember(dataPoints) {
        dataPoints.filter { it.speed >= 5.0 }
    }

    if (drivingPoints.size < 5) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("Not enough driving data for instantaneous consumption",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    // Compute instant consumption and clamp outliers.
    // Formula gives kWh/100km (speed is always km/h in storage). For imperial,
    // multiply by 1/0.621371 to convert to kWh/100mi.
    val efficiencyFactor = if (useImperial) 1.0 / 0.621371 else 1.0
    val clampMax = CLAMP_MAX * efficiencyFactor
    val rawValues = remember(drivingPoints, useImperial) {
        drivingPoints.map { pt ->
            (pt.power / pt.speed * 100.0 * efficiencyFactor).coerceIn(-clampMax, clampMax).toFloat()
        }
    }

    // 5-point centred rolling average — smooths sensor noise
    val avgValues = remember(rawValues) {
        rawValues.mapIndexed { i, _ ->
            val start = (i - ROLLING_WINDOW / 2).coerceAtLeast(0)
            val end   = (i + ROLLING_WINDOW / 2 + 1).coerceAtMost(rawValues.size)
            rawValues.subList(start, end).average().toFloat()
        }
    }

    val rawColor  = AccelerationOrange.copy(alpha = 0.35f)  // raw — faint background
    val avgColor  = BydElectricAzure                        // rolling avg — prominent
    val textColor = MaterialTheme.colorScheme.onSurface
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
    val axisColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    val zeroColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
    var touchPos  by remember { mutableStateOf<Offset?>(null) }

    val modes    = remember(drivingPoints) { drivingPoints.map { it.extractTripModes() } }
    val hasModes = remember(modes) { modes.any { it.driveMode != 0 } }
    val singleDriveMode = remember(modes) { modes.singleDriveModeOrNull() }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegendDot(rawColor,  stringResource(R.string.chart_instant_label))
            Spacer(Modifier.width(20.dp))
            LegendDot(avgColor, stringResource(R.string.chart_avg_5pt))
        }

        Canvas(modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    touchPos = down.position
                    drag(down.id) { change -> touchPos = change.position }
                    touchPos = null
                }
            }
        ) {
            val w = size.width; val h = size.height
            val padL = 80f; val padR = 16f; val padT = 16f; val padB = 40f
            val chartW = w - padL - padR; val chartH = h - padT - padB
            val nc = drawContext.canvas.nativeCanvas

            val allVals = rawValues
            val rawMin = allVals.minOrNull() ?: -30f
            val rawMax = allVals.maxOrNull() ?: 60f
            val yRange = rawMax - rawMin
            val yStep  = when {
                yRange <= 24f  -> 6f
                yRange <= 60f  -> 15f
                yRange <= 120f -> 30f
                else           -> 40f
            }
            val yMin = (rawMin / yStep).toInt() * yStep - yStep
            val yMax = (rawMax / yStep).toInt() * yStep + yStep

            fun xOf(i: Int) = if (drivingPoints.size == 1) padL + chartW / 2f
                              else padL + i / (drivingPoints.size - 1).toFloat() * chartW
            fun yOf(v: Float) = (padT + chartH * (1f - (v - yMin) / (yMax - yMin))).toFloat()

            val totalDuration = if (drivingPoints.size > 1)
                (drivingPoints.last().timestamp - drivingPoints.first().timestamp) / 1000.0 else 0.0

            // ── Drive mode background bands ──────────────────────────────────────
            if (hasModes && drivingPoints.size >= 2) {
                var bandStart = 0
                var bandMode = modes[0].driveMode
                for (i in 1..drivingPoints.size) {
                    val curMode = if (i < drivingPoints.size) modes[i].driveMode else -1
                    if (curMode != bandMode || i == drivingPoints.size) {
                        if (bandMode != 0) {
                            val x0 = xOf(bandStart)
                            val x1 = if (i < drivingPoints.size) xOf(i) else xOf(drivingPoints.size - 1)
                            drawRect(
                                color = driveModeColor(bandMode).copy(alpha = 0.07f),
                                topLeft = Offset(x0, padT),
                                size = Size((x1 - x0).coerceAtLeast(0f), chartH)
                            )
                        }
                        bandStart = i
                        bandMode = curMode
                    }
                }
            }

            val labelPaint = android.graphics.Paint().apply {
                color = textColor.copy(alpha = 0.7f).toArgb(); textSize = 22f; isAntiAlias = true
            }
            val xLabelPaint = android.graphics.Paint().apply {
                color = textColor.copy(alpha = 0.7f).toArgb(); textSize = 20f
                textAlign = android.graphics.Paint.Align.CENTER; isAntiAlias = true
            }

            // Y grid + labels
            var yTick = yMin
            while (yTick <= yMax + 0.5f) {
                val y = yOf(yTick)
                drawLine(gridColor, Offset(padL, y), Offset(w - padR, y), 1f)
                if (yTick > yMin + 0.5f) {
                    labelPaint.textAlign = android.graphics.Paint.Align.RIGHT
                    nc.drawText("%.0f".format(yTick), padL - 6f, y + 8f, labelPaint)
                }
                yTick += yStep
            }

            // Zero line — heavier, regen/drive boundary
            if (yMin < 0 && yMax > 0) {
                drawLine(zeroColor, Offset(padL, yOf(0f)), Offset(w - padR, yOf(0f)), 2f)
            }

            val yAxisPaint = android.graphics.Paint().apply {
                color = textColor.copy(alpha = 0.55f).toArgb(); textSize = 19f
                textAlign = android.graphics.Paint.Align.CENTER; isAntiAlias = true
            }
            nc.save(); nc.rotate(-90f, 18f, padT + chartH / 2f)
            nc.drawText(if (useImperial) "kWh/100mi" else "kWh/100km", 18f, padT + chartH / 2f, yAxisPaint); nc.restore()

            drawLine(axisColor, Offset(padL, padT + chartH), Offset(w - padR, padT + chartH), 1.5f)

            val labelEvery = when {
                drivingPoints.size > 200 -> 40; drivingPoints.size > 100 -> 20
                drivingPoints.size > 50 -> 10; else -> 5
            }
            var lastLabelX = -72f
            drivingPoints.forEachIndexed { i, _ ->
                if (i % labelEvery == 0 || i == drivingPoints.size - 1) {
                    val x = xOf(i)
                    if (x - lastLabelX >= 72f) {
                        val secs = if (drivingPoints.size > 1) (i / (drivingPoints.size - 1).toFloat()) * totalDuration else 0.0
                        nc.drawText("${(secs / 60).toInt()}m", x, h - 8f, xLabelPaint)
                        lastLabelX = x
                    }
                }
            }

            if (drivingPoints.size >= 2) {
                // Raw values — faint area fill
                val rawPath = Path().apply {
                    moveTo(xOf(0), yOf(rawValues[0]))
                    rawValues.drop(1).forEachIndexed { i, v -> lineTo(xOf(i + 1), yOf(v)) }
                }
                drawPath(rawPath, rawColor,
                    style = Stroke(width = 1.5f, cap = StrokeCap.Round))

                // Rolling average — prominent line on top
                val avgPath = Path().apply {
                    moveTo(xOf(0), yOf(avgValues[0]))
                    avgValues.drop(1).forEachIndexed { i, v -> lineTo(xOf(i + 1), yOf(v)) }
                }
                drawPath(avgPath, avgColor,
                    style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }

            drawDriveModeLabel(singleDriveMode, padR, padT)

            touchPos?.let { tp ->
                if (tp.x in padL..(w - padR) && drivingPoints.size > 1) {
                    val idx = ((tp.x - padL) / chartW * (drivingPoints.size - 1))
                        .roundToInt().coerceIn(0, drivingPoints.size - 1)
                    val secs = (idx / (drivingPoints.size - 1).toFloat()) * totalDuration
                    val clockTime = java.time.Instant.ofEpochMilli(drivingPoints[idx].timestamp)
                        .atZone(java.time.ZoneId.systemDefault())
                        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
                    drawCrosshair(
                        cx = xOf(idx), cy = yOf(rawValues[idx]), w = w,
                        padL = padL, padR = padR, padT = padT, chartH = chartH,
                        line1 = "Instant: %.1f ${if (useImperial) "kWh/100mi" else "kWh/100km"}".format(rawValues[idx]),
                        line2 = "Avg(5pt): %.1f ${if (useImperial) "kWh/100mi" else "kWh/100km"}  |  %.0f ${if (useImperial) "mph" else "km/h"}".format(
                            avgValues[idx], drivingPoints[idx].speed),
                        line3 = "${(secs / 60).toInt()}m ${(secs % 60).toInt()}s  $clockTime",
                        accentColor = avgColor
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

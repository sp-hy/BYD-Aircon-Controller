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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.byd.tripstats.R
import com.byd.tripstats.data.local.entity.TripDataPointEntity
import com.byd.tripstats.ui.theme.AccelerationOrange
import com.byd.tripstats.ui.theme.BydElectricBlue
import com.byd.tripstats.ui.theme.RegenGreen
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

@Composable
fun BatteryVoltageChart(
    dataPoints: List<TripDataPointEntity>,
    modifier: Modifier = Modifier
) {
    if (dataPoints.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("No data available", style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val hvColor   = BydElectricBlue    // HV bus voltage
    val minColor  = RegenGreen         // Cell voltage min
    val maxColor  = AccelerationOrange // Cell voltage max
    val textColor = MaterialTheme.colorScheme.onSurface
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
    val axisColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    var touchPos  by remember { mutableStateOf<Offset?>(null) }

    val modes    = remember(dataPoints) { dataPoints.map { it.extractTripModes() } }
    val hasModes = remember(modes) { modes.any { it.driveMode != 0 } }
    val singleDriveMode = remember(modes) { modes.singleDriveModeOrNull() }

    Column(modifier = modifier) {

        // ── Legend ────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegendDot(hvColor,  "HV Bus (V)")
            Spacer(Modifier.width(20.dp))
            LegendDot(minColor, stringResource(R.string.charging_chart_cell_min))
            Spacer(Modifier.width(20.dp))
            LegendDot(maxColor, stringResource(R.string.charging_chart_cell_max))
        }

        val strCellsV = stringResource(R.string.chart_cells_voltage_axis)

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
            val padL = 80f; val padR = 70f; val padT = 16f; val padB = 40f
            val chartW = w - padL - padR; val chartH = h - padT - padB
            val nc = drawContext.canvas.nativeCanvas

            val hvValues  = dataPoints.map { it.batteryTotalVoltage.toFloat() }
            val minValues = dataPoints.map { (it.batteryCellVoltageMin * 100).roundToInt() / 100f }
            val maxValues = dataPoints.map { (it.batteryCellVoltageMax * 100).roundToInt() / 100f }

            // Two separate Y axes conceptually, but we normalise together:
            // HV bus ~500–600 V; cell voltages ~2.8–3.5 V — too different to share.
            // Solution: dual-axis. Left = HV bus (V), right = cell voltage (V).
            // We draw them on separate scales mapped to the same canvas height.
            val hvMin   = (hvValues.minOrNull()  ?: 400f) - 10f
            val hvMax   = (hvValues.maxOrNull()  ?: 600f) + 10f
            val cellMin = (minValues.minOrNull() ?: 2.8f) - 0.05f
            val cellMax = (maxValues.maxOrNull() ?: 3.5f) + 0.05f

            fun xOf(i: Int) = if (dataPoints.size == 1) padL + chartW / 2f
                              else padL + i / (dataPoints.size - 1).toFloat() * chartW
            fun yOfHv(v: Float)   = (padT + chartH * (1f - (v - hvMin)   / (hvMax   - hvMin).coerceAtLeast(1f))).toFloat()
            fun yOfCell(v: Float) = (padT + chartH * (1f - (v - cellMin) / (cellMax - cellMin).coerceAtLeast(0.01f))).toFloat()

            val totalDuration = if (dataPoints.size > 1)
                (dataPoints.last().timestamp - dataPoints.first().timestamp) / 1000.0 else 0.0

            // ── Drive mode background bands ──────────────────────────────────────
            if (hasModes && dataPoints.size >= 2) {
                var bandStart = 0
                var bandMode = modes[0].driveMode
                for (i in 1..dataPoints.size) {
                    val curMode = if (i < dataPoints.size) modes[i].driveMode else -1
                    if (curMode != bandMode || i == dataPoints.size) {
                        if (bandMode != 0) {
                            val x0 = xOf(bandStart)
                            val x1 = if (i < dataPoints.size) xOf(i) else xOf(dataPoints.size - 1)
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
                color = textColor.copy(alpha = 0.7f).toArgb(); textSize = 20f; isAntiAlias = true
            }
            val xLabelPaint = android.graphics.Paint().apply {
                color = textColor.copy(alpha = 0.7f).toArgb(); textSize = 20f
                textAlign = android.graphics.Paint.Align.CENTER; isAntiAlias = true
            }

            // Left Y axis — HV bus (V), 5 ticks
            val hvStep = ((hvMax - hvMin) / 5).coerceAtLeast(1f)
            var hvTick = (hvMin / hvStep).toInt() * hvStep
            while (hvTick <= hvMax + 0.5f) {
                val y = yOfHv(hvTick)
                if (y >= padT + chartH - 4f) {
                    hvTick += hvStep
                    continue
                }
                drawLine(gridColor, Offset(padL, y), Offset(w - padR, y), 1f)
                labelPaint.textAlign = android.graphics.Paint.Align.RIGHT
                nc.drawText("${hvTick.toInt()}", padL - 6f, y + 7f, labelPaint)
                hvTick += hvStep
            }

            // Left axis label
            val yAxisPaint = android.graphics.Paint().apply {
                color = hvColor.copy(alpha = 0.7f).toArgb(); textSize = 19f
                textAlign = android.graphics.Paint.Align.CENTER; isAntiAlias = true
            }
            nc.save(); nc.rotate(-90f, 18f, padT + chartH / 2f)
            nc.drawText("HV (V)", 18f, padT + chartH / 2f, yAxisPaint); nc.restore()

            // Right Y axis — cell voltage (V)
            val cellStep = ((cellMax - cellMin) / 5).coerceAtLeast(0.01f)
            var cellTick = (cellMin / cellStep).toInt() * cellStep
            val rightAxisPaint = android.graphics.Paint().apply {
                color = minColor.copy(alpha = 0.7f).toArgb(); textSize = 20f
                textAlign = android.graphics.Paint.Align.LEFT; isAntiAlias = true
            }
            while (cellTick <= cellMax + 0.005f) {
                val y = yOfCell(cellTick)
                if (y < padT + chartH - 4f) {
                    labelPaint.textAlign = android.graphics.Paint.Align.LEFT
                    nc.drawText("%.2f".format(cellTick), w - padR + 4f, y + 7f, rightAxisPaint)
                }
                cellTick += cellStep
            }
            val rightAxisLabelPaint = android.graphics.Paint().apply {
                color = minColor.copy(alpha = 0.7f).toArgb(); textSize = 19f
                textAlign = android.graphics.Paint.Align.CENTER; isAntiAlias = true
            }
            nc.save(); nc.rotate(90f, w - 14f, padT + chartH / 2f)
            nc.drawText(strCellsV, w - 14f, padT + chartH / 2f, rightAxisLabelPaint); nc.restore()

            // X axis
            drawLine(axisColor, Offset(padL, padT + chartH), Offset(w - padR, padT + chartH), 1.5f)
            val labelEvery = when {
                dataPoints.size > 200 -> 40; dataPoints.size > 100 -> 20
                dataPoints.size > 50  -> 10; else -> 5
            }
            var lastLabelX = -72f
            dataPoints.forEachIndexed { i, _ ->
                if (i % labelEvery == 0 || i == dataPoints.size - 1) {
                    val x = xOf(i)
                    if (x - lastLabelX >= 72f) {
                        val secs = if (dataPoints.size > 1) (i / (dataPoints.size - 1).toFloat()) * totalDuration else 0.0
                        nc.drawText("${(secs / 60).toInt()}m", x, h - 8f, xLabelPaint)
                        lastLabelX = x
                    }
                }
            }

            if (dataPoints.size >= 2) {
                // Cell min/max band fill
                val bandPath = Path().apply {
                    moveTo(xOf(0), yOfCell(maxValues[0]))
                    maxValues.drop(1).forEachIndexed { i, v -> lineTo(xOf(i + 1), yOfCell(v)) }
                    minValues.indices.reversed().forEach { i -> lineTo(xOf(i), yOfCell(minValues[i])) }
                    close()
                }
                drawPath(bandPath, Brush.verticalGradient(
                    listOf(maxColor.copy(alpha = 0.12f), minColor.copy(alpha = 0.12f)),
                    startY = yOfCell(maxValues.max()), endY = yOfCell(minValues.min())
                ))

                // Cell min line
                val minPath = Path().apply {
                    moveTo(xOf(0), yOfCell(minValues[0]))
                    minValues.drop(1).forEachIndexed { i, v -> lineTo(xOf(i + 1), yOfCell(v)) }
                }
                drawPath(minPath, minColor.copy(alpha = 0.9f),
                    style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round))

                // Cell max line
                val maxPath = Path().apply {
                    moveTo(xOf(0), yOfCell(maxValues[0]))
                    maxValues.drop(1).forEachIndexed { i, v -> lineTo(xOf(i + 1), yOfCell(v)) }
                }
                drawPath(maxPath, maxColor.copy(alpha = 0.9f),
                    style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round))

                // HV bus line (on top)
                val hvPath = Path().apply {
                    moveTo(xOf(0), yOfHv(hvValues[0]))
                    hvValues.drop(1).forEachIndexed { i, v -> lineTo(xOf(i + 1), yOfHv(v)) }
                }
                drawPath(hvPath, hvColor,
                    style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }

            drawDriveModeLabel(singleDriveMode, padR, padT)

            // Crosshair
            touchPos?.let { tp ->
                if (tp.x in padL..(w - padR) && dataPoints.size > 1) {
                    val idx = ((tp.x - padL) / chartW * (dataPoints.size - 1))
                        .roundToInt().coerceIn(0, dataPoints.size - 1)
                    val secs = (idx / (dataPoints.size - 1).toFloat()) * totalDuration
                    val clockTime = java.time.Instant.ofEpochMilli(dataPoints[idx].timestamp)
                        .atZone(java.time.ZoneId.systemDefault())
                        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
                    drawCrosshair(
                        cx = xOf(idx), cy = yOfHv(hvValues[idx]), w = w,
                        padL = padL, padR = padR, padT = padT, chartH = chartH,
                        line1 = "HV: ${hvValues[idx].toInt()} V  |  Cell: %.3f – %.3f V".format(
                            minValues[idx], maxValues[idx]),
                        line2 = "Spread: %.3f V".format(maxValues[idx] - minValues[idx]),
                        line3 = "${(secs / 60).toInt()}m ${(secs % 60).toInt()}s  $clockTime",
                        accentColor = hvColor
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

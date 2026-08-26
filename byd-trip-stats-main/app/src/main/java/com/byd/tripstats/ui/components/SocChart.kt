package com.byd.tripstats.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.tripstats.data.local.entity.TripDataPointEntity
import com.byd.tripstats.ui.theme.BydElectricBlue
import com.byd.tripstats.ui.theme.MotorViolet
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width

@Composable
fun SocChart(
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

    val bmsColor   = BydElectricBlue    // BMS SoC
    val panelColor = MotorViolet        // Instrument panel SoC
    val textColor  = MaterialTheme.colorScheme.onSurface
    val gridColor  = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
    val axisColor  = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    var touchPos   by remember { mutableStateOf<Offset?>(null) }

    // Only show socPanel line if the data actually has non-zero panel values
    // (pre-v2 trips will have all zeros)
    val hasPanelData = remember(dataPoints) { dataPoints.any { it.socPanel > 0 } }

    val modes   = remember(dataPoints) { dataPoints.map { it.extractTripModes() } }
    val hasModes = remember(modes) { modes.any { it.driveMode != 0 } }
    val singleDriveMode = remember(modes) { modes.singleDriveModeOrNull() }

    Column(modifier = modifier) {

        // ── Legend ────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegendDot(bmsColor, "BMS SoC")
            if (hasPanelData) {
                Spacer(Modifier.width(20.dp))
                LegendDot(panelColor, "Panel SoC")
            }
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

            val bmsValues   = dataPoints.map { it.soc.toFloat() }
            val panelValues = dataPoints.map { it.socPanel.toFloat() }

            val yMin = 0.0; val yMax = 100.0; val yStep = 20.0

            fun xOf(i: Int) = if (dataPoints.size == 1) padL + chartW / 2f
            else padL + i / (dataPoints.size - 1).toFloat() * chartW
            fun yOf(v: Float) = (padT + chartH * (1.0 - (v - yMin) / (yMax - yMin))).toFloat()

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
                color = textColor.copy(alpha = 0.7f).toArgb(); textSize = 22f; isAntiAlias = true
            }
            val xLabelPaint = android.graphics.Paint().apply {
                color = textColor.copy(alpha = 0.7f).toArgb(); textSize = 20f
                textAlign = android.graphics.Paint.Align.CENTER; isAntiAlias = true
            }

            // Y grid + labels
            var yTick = yMin
            while (yTick <= yMax + 0.01) {
                val y = yOf(yTick.toFloat())
                drawLine(gridColor, Offset(padL, y), Offset(w - padR, y), 1f)
                labelPaint.textAlign = android.graphics.Paint.Align.RIGHT
                nc.drawText("${yTick.toInt()}%", padL - 6f, y + 8f, labelPaint)
                yTick += yStep
            }

            val yAxisPaint = android.graphics.Paint().apply {
                color = textColor.copy(alpha = 0.55f).toArgb(); textSize = 19f
                textAlign = android.graphics.Paint.Align.CENTER; isAntiAlias = true
            }
            nc.save(); nc.rotate(-90f, 18f, padT + chartH / 2f)
            nc.drawText("SoC %", 18f, padT + chartH / 2f, yAxisPaint); nc.restore()

            drawLine(axisColor, Offset(padL, padT + chartH), Offset(w - padR, padT + chartH), 1.5f)

            val labelEvery = when {
                dataPoints.size > 200 -> 40; dataPoints.size > 100 -> 20
                dataPoints.size > 50  -> 10; else -> 5
            }
            val minLabelGap = 72f; var lastLabelX = -minLabelGap
            dataPoints.forEachIndexed { i, _ ->
                if (i % labelEvery == 0 || i == dataPoints.size - 1) {
                    val x = xOf(i)
                    if (x - lastLabelX >= minLabelGap) {
                        val secs = if (dataPoints.size > 1)
                            (i / (dataPoints.size - 1).toFloat()) * totalDuration else 0.0
                        nc.drawText("${(secs / 60).toInt()}m", x, h - 8f, xLabelPaint)
                        lastLabelX = x
                    }
                }
            }

            if (dataPoints.size >= 2) {
                // BMS area fill
                val areaPath = Path().apply {
                    moveTo(xOf(0), yOf(bmsValues[0]))
                    bmsValues.drop(1).forEachIndexed { i, v -> lineTo(xOf(i + 1), yOf(v)) }
                    lineTo(xOf(dataPoints.size - 1), padT + chartH)
                    lineTo(xOf(0), padT + chartH); close()
                }
                drawPath(areaPath, Brush.verticalGradient(
                    listOf(bmsColor.copy(alpha = 0.25f), bmsColor.copy(alpha = 0f)),
                    startY = yOf(bmsValues.max()), endY = padT + chartH
                ))

                // BMS line
                val bmsPath = Path().apply {
                    moveTo(xOf(0), yOf(bmsValues[0]))
                    bmsValues.drop(1).forEachIndexed { i, v -> lineTo(xOf(i + 1), yOf(v)) }
                }
                drawPath(bmsPath, bmsColor,
                    style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round))

                // Panel SoC line (dashed-style: thinner, different color)
                if (hasPanelData) {
                    val panelPath = Path().apply {
                        moveTo(xOf(0), yOf(panelValues[0]))
                        panelValues.drop(1).forEachIndexed { i, v -> lineTo(xOf(i + 1), yOf(v)) }
                    }
                    drawPath(panelPath, panelColor.copy(alpha = 0.85f),
                        style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
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

                    val line1 = if (hasPanelData)
                        "BMS: %.1f%%  Panel: %d%%".format(bmsValues[idx], panelValues[idx].toInt())
                    else
                        "%.1f%%".format(bmsValues[idx])

                    drawCrosshair(
                        cx = xOf(idx), cy = yOf(bmsValues[idx]), w = w,
                        padL = padL, padR = padR, padT = padT, chartH = chartH,
                        line1 = line1,
                        line2 = "${(secs / 60).toInt()}m ${(secs % 60).toInt()}s",
                        line3 = clockTime,
                        accentColor = bmsColor
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

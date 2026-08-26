package com.byd.tripstats.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
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
import com.byd.tripstats.ui.screens.dashboard.TyrePressureUnit
import com.byd.tripstats.ui.theme.AccelerationOrange
import com.byd.tripstats.ui.theme.BydElectricAzure
import com.byd.tripstats.ui.theme.MotorViolet
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
fun TyrePressureChart(
    dataPoints: List<TripDataPointEntity>,
    modifier: Modifier = Modifier
) {
    // Filter to points that have at least some pressure data
    val validPoints = remember(dataPoints) {
        dataPoints.filter { it.tyrePressureLF > 0.1 || it.tyrePressureRF > 0.1 }
    }

    if (validPoints.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("No tyre pressure data recorded on this trip",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    // Read the unit preference the user set on the dashboard tyre icon tap
    val context   = LocalContext.current
    val tyrePrefs = remember { context.getSharedPreferences("tyre_unit_prefs", 0) }
    var unit by remember {
        mutableStateOf(
            TyrePressureUnit.entries.getOrElse(
                tyrePrefs.getInt("unit", TyrePressureUnit.BAR.ordinal)
            ) { TyrePressureUnit.BAR }
        )
    }

    val lfColor   = BydElectricAzure
    val rfColor   = AccelerationOrange
    val lrColor   = RegenGreen
    val rrColor   = MotorViolet
    val textColor = MaterialTheme.colorScheme.onSurface
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
    val axisColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    var touchPos  by remember { mutableStateOf<Offset?>(null) }

    val modes    = remember(validPoints) { validPoints.map { it.extractTripModes() } }
    val hasModes = remember(modes) { modes.any { it.driveMode != 0 } }
    val singleDriveMode = remember(modes) { modes.singleDriveModeOrNull() }

    // Convert all pressures to the selected unit
    val lfVals = validPoints.map { it.tyrePressureLF.psiToUnit(unit).toFloat() }
    val rfVals = validPoints.map { it.tyrePressureRF.psiToUnit(unit).toFloat() }
    val lrVals = validPoints.map { it.tyrePressureLR.psiToUnit(unit).toFloat() }
    val rrVals = validPoints.map { it.tyrePressureRR.psiToUnit(unit).toFloat() }

    Column(modifier = modifier) {

        // ── Legend + unit selector ───────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Wheel legend
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendDot(lfColor, "LF"); LegendDot(rfColor, "RF")
                LegendDot(lrColor, "LR"); LegendDot(rrColor, "RR")
            }
            // Unit chips — tap to switch; persists to same SharedPrefs as dashboard
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TyrePressureUnit.entries.forEach { u ->
                    val selected = u == unit
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable {
                                unit = u
                                tyrePrefs.edit().putInt("unit", u.ordinal).apply()
                            }
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            u.label(),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
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
            // padL must accommodate the widest tick label for the current unit:
            //   bar  →  "2.6"   (4 chars)  →  60f
            //   psi  →  "38.5"  (4 chars)  →  68f
            //   kPa  →  "270"   (3 chars but wider textSize and a rotated axis label)  →  80f
            val padL = when (unit) {
                TyrePressureUnit.BAR -> 60f
                TyrePressureUnit.PSI -> 68f
                TyrePressureUnit.KPA -> 80f
            }
            val padR = 16f; val padT = 16f; val padB = 40f
            val chartW = w - padL - padR; val chartH = h - padT - padB
            val nc = drawContext.canvas.nativeCanvas

            val allVals = (lfVals + rfVals + lrVals + rrVals).filter { it > 0.1f }
            val rawMin = allVals.minOrNull() ?: 1.5f
            val rawMax = allVals.maxOrNull() ?: 3.5f
            val padding = (rawMax - rawMin) * 0.15f
            val yMin = (rawMin - padding).coerceAtLeast(0f)
            val yMax = rawMax + padding
            // Target ~5 evenly-spaced ticks regardless of unit.
            // Snap to a "nice" step so labels are round numbers.
            val roughStep = (yMax - yMin) / 5f
            val yStep = when {
                roughStep <= 0.1f  -> 0.1f
                roughStep <= 0.2f  -> 0.2f
                roughStep <= 0.5f  -> 0.5f
                roughStep <= 1.0f  -> 1.0f
                roughStep <= 3.0f  -> 2.0f
                roughStep <= 7.0f  -> 5.0f
                roughStep <= 15.0f -> 10.0f
                roughStep <= 30.0f -> 20.0f
                roughStep <= 75.0f -> 50.0f
                else               -> 100.0f
            }

            fun xOf(i: Int) = if (validPoints.size == 1) padL + chartW / 2f
                              else padL + i / (validPoints.size - 1).toFloat() * chartW
            fun yOf(v: Float) = (padT + chartH * (1f - (v - yMin) / (yMax - yMin))).toFloat()

            val totalDuration = if (validPoints.size > 1)
                (validPoints.last().timestamp - validPoints.first().timestamp) / 1000.0 else 0.0

            // ── Drive mode background bands ──────────────────────────────────────
            if (hasModes && validPoints.size >= 2) {
                var bandStart = 0
                var bandMode = modes[0].driveMode
                for (i in 1..validPoints.size) {
                    val curMode = if (i < validPoints.size) modes[i].driveMode else -1
                    if (curMode != bandMode || i == validPoints.size) {
                        if (bandMode != 0) {
                            val x0 = xOf(bandStart)
                            val x1 = if (i < validPoints.size) xOf(i) else xOf(validPoints.size - 1)
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

            var yTick = (yMin / yStep).toInt() * yStep
            while (yTick <= yMax + 0.01f) {
                val y = yOf(yTick)
                // Skip ticks that fall on or below the X-axis line
                if (y <= padT + chartH) {
                    drawLine(gridColor, Offset(padL, y), Offset(w - padR, y), 1f)
                    labelPaint.textAlign = android.graphics.Paint.Align.RIGHT
                    val tickLabel = when (unit) {
                        TyrePressureUnit.BAR -> "%.1f".format(yTick)
                        TyrePressureUnit.PSI -> "%.0f".format(yTick)
                        TyrePressureUnit.KPA -> "%.0f".format(yTick)
                    }
                    nc.drawText(tickLabel, padL - 6f, y + 7f, labelPaint)
                }
                yTick += yStep
            }

            val yAxisPaint = android.graphics.Paint().apply {
                color = textColor.copy(alpha = 0.55f).toArgb(); textSize = 19f
                textAlign = android.graphics.Paint.Align.CENTER; isAntiAlias = true
            }
            nc.save(); nc.rotate(-90f, 18f, padT + chartH / 2f)
            nc.drawText(unit.label(), 18f, padT + chartH / 2f, yAxisPaint); nc.restore()

            drawLine(axisColor, Offset(padL, padT + chartH), Offset(w - padR, padT + chartH), 1.5f)

            val labelEvery = when {
                validPoints.size > 200 -> 40; validPoints.size > 100 -> 20
                validPoints.size > 50 -> 10; else -> 5
            }
            var lastLabelX = -72f
            validPoints.forEachIndexed { i, _ ->
                if (i % labelEvery == 0 || i == validPoints.size - 1) {
                    val x = xOf(i)
                    if (x - lastLabelX >= 72f) {
                        val secs = if (validPoints.size > 1) (i / (validPoints.size - 1).toFloat()) * totalDuration else 0.0
                        nc.drawText("${(secs / 60).toInt()}m", x, h - 8f, xLabelPaint)
                        lastLabelX = x
                    }
                }
            }

            if (validPoints.size >= 2) {
                fun drawLine(values: List<Float>, color: Color, strokeWidth: Float = 2.5f) {
                    val path = Path().apply {
                        moveTo(xOf(0), yOf(values[0]))
                        values.drop(1).forEachIndexed { i, v -> lineTo(xOf(i + 1), yOf(v)) }
                    }
                    drawPath(path, color.copy(alpha = 0.9f),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }

                drawLine(lrVals, lrColor)
                drawLine(rrVals, rrColor)
                drawLine(lfVals, lfColor, 3f)
                drawLine(rfVals, rfColor, 3f)
            }

            drawDriveModeLabel(singleDriveMode, padR, padT)

            touchPos?.let { tp ->
                if (tp.x in padL..(w - padR) && validPoints.size > 1) {
                    val idx = ((tp.x - padL) / chartW * (validPoints.size - 1))
                        .roundToInt().coerceIn(0, validPoints.size - 1)
                    val secs = (idx / (validPoints.size - 1).toFloat()) * totalDuration
                    val clockTime = java.time.Instant.ofEpochMilli(validPoints[idx].timestamp)
                        .atZone(java.time.ZoneId.systemDefault())
                        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
                    drawCrosshair(
                        cx = xOf(idx), cy = yOf(lfVals[idx]), w = w,
                        padL = padL, padR = padR, padT = padT, chartH = chartH,
                        line1 = "LF ${unit.fmt(validPoints[idx].tyrePressureLF)}  RF ${unit.fmt(validPoints[idx].tyrePressureRF)}  ${unit.label()}",
                        line2 = "LR ${unit.fmt(validPoints[idx].tyrePressureLR)}  RR ${unit.fmt(validPoints[idx].tyrePressureRR)}",
                        line3 = "${(secs / 60).toInt()}m ${(secs % 60).toInt()}s  $clockTime",
                        accentColor = lfColor
                    )
                }
            }
        }
    }
}

// Inline conversions — TyrePressureUnit is public but its extension fns are private
private fun Double.psiToUnit(unit: TyrePressureUnit): Double = when (unit) {
    TyrePressureUnit.BAR -> this / 14.5038
    TyrePressureUnit.PSI -> this
    TyrePressureUnit.KPA -> this * 6.89476
}
private fun TyrePressureUnit.label(): String = when (this) {
    TyrePressureUnit.BAR -> "bar"
    TyrePressureUnit.PSI -> "psi"
    TyrePressureUnit.KPA -> "kPa"
}
private fun TyrePressureUnit.fmt(psi: Double): String = when (this) {
    TyrePressureUnit.BAR -> "%.2f".format(psi.psiToUnit(this))
    TyrePressureUnit.PSI -> "%.1f".format(psi.psiToUnit(this))
    TyrePressureUnit.KPA -> "%.0f".format(psi.psiToUnit(this))
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

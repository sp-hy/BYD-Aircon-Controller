package com.byd.tripstats.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import com.byd.tripstats.data.local.entity.TripDataPointEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.byd.tripstats.ui.theme.ChargingYellow
import androidx.compose.ui.res.stringResource
import com.byd.tripstats.R
import kotlin.math.roundToInt

private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

@Composable
fun EnergyConsumptionChart(
    dataPoints: List<TripDataPointEntity>,
    modifier: Modifier = Modifier,
    maxPoints: Int? = null
) {
    if (dataPoints.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("No data available", style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val fullValues = remember(dataPoints) {
        // Integrate positive instantaneous power (kW) × dt to produce a smooth discharge
        // curve from trip start. Using totalDischarge directly is unreliable: Car
        // frequently reports 0 for the first minutes of a trip and then
        // jumps to the final value at the end, producing a flat line that spikes at the
        // very end of the chart. Power-integration reflects what the car is actually
        // drawing in real time and matches the user's felt consumption.
        //
        // This MUST run on full-resolution samples: the gap-guard below rejects any interval
        // longer than 60s, so integrating pre-condensed points (where neighbours are minutes
        // apart) would discard nearly every interval and flatline the chart. Condensing
        // therefore happens AFTER integration, on the monotone curve.
        val result = ArrayList<Float>(dataPoints.size)
        var cumulative = 0.0
        result.add(0f)
        for (i in 1 until dataPoints.size) {
            val a = dataPoints[i - 1]
            val b = dataPoints[i]
            val dtH = (b.timestamp - a.timestamp).coerceAtLeast(0L) / 3_600_000.0
            // Only positive power counts as discharge. Gap-guard prevents a long stale
            // interval (app backgrounded, etc.) from injecting a phantom bar.
            if (dtH in 0.0..(60.0 / 3600.0) && a.power > 0.0) {
                cumulative += a.power * dtH
            }
            result.add(cumulative.toFloat())
        }
        result
    }

    if (fullValues.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("Insufficient data", style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    // Decimate the already-integrated curve for display. It is monotone and smooth, so
    // index-sampling it is loss-free for an overview — and the trip-end total is preserved by
    // always keeping the final sample. With maxPoints == null the chart renders full detail.
    val (points, values) = remember(dataPoints, fullValues, maxPoints) {
        if (maxPoints == null || dataPoints.size <= maxPoints) {
            dataPoints to fullValues
        } else {
            val step = (dataPoints.size + maxPoints - 1) / maxPoints
            val idx = buildList {
                var i = 0
                while (i < dataPoints.size) { add(i); i += step }
                if (last() != dataPoints.lastIndex) add(dataPoints.lastIndex)
            }
            idx.map { dataPoints[it] } to idx.map { fullValues[it] }
        }
    }

    val lineColor = ChargingYellow
    val textColor = MaterialTheme.colorScheme.onSurface
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
    val axisColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    var touchPos by remember { mutableStateOf<Offset?>(null) }
    val intoTrip = stringResource(R.string.chart_into_trip)

    val modes    = remember(points) { points.map { it.extractTripModes() } }
    val hasModes = remember(modes) { modes.any { it.driveMode != 0 } }
    val singleDriveMode = remember(modes) { modes.singleDriveModeOrNull() }

    Canvas(modifier = modifier.fillMaxSize().pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown()
            touchPos = down.position
            drag(down.id) { change -> touchPos = change.position }
            touchPos = null
        }
    }) {
        val w = size.width; val h = size.height
        val padL = 80f; val padR = 16f; val padT = 16f; val padB = 40f
        val chartW = w - padL - padR; val chartH = h - padT - padB
        val nc = drawContext.canvas.nativeCanvas
        val rawMax = values.max().coerceAtLeast(1f)
        val yStep = when {
            rawMax < 5f -> 1.0; rawMax < 20f -> 2.0; rawMax < 50f -> 5.0; rawMax < 100f -> 10.0; else -> 20.0
        }
        val yMin = 0.0; val yMax = (rawMax / yStep).toInt() * yStep + yStep
        fun xOf(i: Int) = if (values.size == 1) padL + chartW / 2f
                          else padL + i / (values.size - 1).toFloat() * chartW
        fun yOf(v: Float): Float {
            return (padT + chartH * (1.0 - (v - yMin) / (yMax - yMin))).toFloat()
        }
        val totalDuration = if (points.size > 1)
            (points.last().timestamp - points.first().timestamp) / 1000.0 else 0.0

        // ── Drive mode background bands ──────────────────────────────────────
        if (hasModes && points.size >= 2) {
            var bandStart = 0
            var bandMode = modes[0].driveMode
            for (i in 1..points.size) {
                val curMode = if (i < points.size) modes[i].driveMode else -1
                if (curMode != bandMode || i == points.size) {
                    if (bandMode != 0) {
                        val x0 = xOf(bandStart)
                        val x1 = if (i < points.size) xOf(i) else xOf(points.size - 1)
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
        var yTick = yMin
        while (yTick <= yMax + 0.01) {
            val y = yOf(yTick.toFloat())
            drawLine(gridColor, Offset(padL, y), Offset(w - padR, y), 1f)
            labelPaint.textAlign = android.graphics.Paint.Align.RIGHT
            nc.drawText("%.1f".format(yTick), padL - 6f, y + 8f, labelPaint)
            yTick += yStep
        }
        val yAxisPaint = android.graphics.Paint().apply {
            color = textColor.copy(alpha = 0.55f).toArgb(); textSize = 19f
            textAlign = android.graphics.Paint.Align.CENTER; isAntiAlias = true
        }
        nc.save(); nc.rotate(-90f, 18f, padT + chartH / 2f)
        nc.drawText("kWh", 18f, padT + chartH / 2f, yAxisPaint); nc.restore()
        drawLine(axisColor, Offset(padL, padT + chartH), Offset(w - padR, padT + chartH), 1.5f)
        val labelEvery = when {
            points.size > 200 -> 40; points.size > 100 -> 20; points.size > 50 -> 10; else -> 5
        }
        val minLabelGap = 72f
        var lastLabelX = -minLabelGap
        points.forEachIndexed { i, _ ->
            if (i % labelEvery == 0 || i == points.size - 1) {
                val x = xOf(i)
                if (x - lastLabelX >= minLabelGap) {
                    val secs = if (points.size > 1) (i / (points.size - 1).toFloat()) * totalDuration else 0.0
                    nc.drawText("%d:%02d".format((secs / 60).toInt(), (secs % 60).toInt()), x, h - 8f, xLabelPaint)
                    lastLabelX = x
                }
            }
        }
        if (values.size >= 2) {
            val areaPath = Path().apply {
                moveTo(xOf(0), yOf(values[0]))
                values.drop(1).forEachIndexed { i, v -> lineTo(xOf(i + 1), yOf(v)) }
                lineTo(xOf(values.size - 1), padT + chartH); lineTo(xOf(0), padT + chartH); close()
            }
            drawPath(areaPath, Brush.verticalGradient(
                colors = listOf(ChargingYellow.copy(alpha = 0.40f), ChargingYellow.copy(alpha = 0f)),
                startY = yOf(values.max()), endY = padT + chartH
            ))
            val linePath = Path().apply {
                moveTo(xOf(0), yOf(values[0]))
                values.drop(1).forEachIndexed { i, v -> lineTo(xOf(i + 1), yOf(v)) }
            }
            drawPath(linePath, lineColor, style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        drawDriveModeLabel(singleDriveMode, padR, padT)
        touchPos?.let { tp ->
            if (tp.x in padL..(w - padR) && values.size > 1) {
                val idx = ((tp.x - padL) / chartW * (values.size - 1)).roundToInt().coerceIn(0, values.size - 1)
                val secs = (idx / (values.size - 1).toFloat()) * totalDuration
                val realTime = timeFmt.format(Date(points[idx].timestamp))
                val durationStr = "+%d:%02d $intoTrip".format((secs / 60).toInt(), (secs % 60).toInt())
                drawCrosshair(
                    cx = xOf(idx), cy = yOf(values[idx]), w = w,
                    padL = padL, padR = padR, padT = padT, chartH = chartH,
                    line1 = "%.2f kWh".format(values[idx]),
                    line2 = realTime,
                    line3 = durationStr,
                    accentColor = lineColor
                )
            }
        }
    }
}

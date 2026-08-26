package com.byd.tripstats.ui.screens.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.byd.tripstats.data.model.BatteryVoltageHistoryPoint
import com.byd.tripstats.data.model.VehicleTelemetry
import com.byd.tripstats.data.preferences.SocSource
import com.byd.tripstats.sdk.VehicleTelemetrySnapshot
import androidx.compose.ui.res.stringResource
import com.byd.tripstats.R
import com.byd.tripstats.ui.components.BrandNavigationBar
import com.byd.tripstats.ui.components.drawCrosshair
import com.byd.tripstats.ui.theme.BatteryBlue
import com.byd.tripstats.ui.theme.MotorViolet
import com.byd.tripstats.ui.theme.RegenGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Battery12vHistoryDialog(
    historyPoints: List<BatteryVoltageHistoryPoint>,
    telemetry: VehicleTelemetry,
    vehicleSnapshot: VehicleTelemetrySnapshot?,
    socSource: SocSource,
    onDismiss: () -> Unit
) {
    val liveTimestamp = remember(
        telemetry.battery12vVoltage,
        telemetry.batteryTotalVoltage,
        vehicleSnapshot?.batteryTotalVoltage
    ) {
        System.currentTimeMillis()
    }
    val liveHvVoltage = vehicleSnapshot?.batteryTotalVoltage?.takeIf { it > 0 }
        ?: telemetry.batteryTotalVoltage.takeIf { it > 0 }
        ?: 0
    val mergedPoints = remember(historyPoints, telemetry.battery12vVoltage, liveTimestamp, liveHvVoltage) {
        val livePoint = telemetry.battery12vVoltage.takeIf { it > 0.0 }?.let {
            BatteryVoltageHistoryPoint(
                timestamp = liveTimestamp,
                battery12vVoltage = it,
                batteryTotalVoltage = liveHvVoltage,
                isChargingSample = telemetry.isCharging,
                soc = telemetry.soc,
                socPanel = telemetry.socPanel
            )
        }
        val base = historyPoints.toMutableList()
        if (livePoint != null) {
            val last = base.lastOrNull()
            val shouldAppend = last == null ||
                livePoint.timestamp - last.timestamp > 60_000L ||
                kotlin.math.abs(livePoint.battery12vVoltage - last.battery12vVoltage) >= 0.01
            if (shouldAppend) base += livePoint
        }
        base.sortedBy { it.timestamp }
    }

    val isSplitScreen = com.byd.tripstats.ui.rememberIsSplitScreen()
    val latest = mergedPoints.lastOrNull()
    val min12v = mergedPoints.minOfOrNull { it.battery12vVoltage }
    val max12v = mergedPoints.maxOfOrNull { it.battery12vVoltage }
    val delta12v = if (mergedPoints.size >= 2) {
        mergedPoints.last().battery12vVoltage - mergedPoints.first().battery12vVoltage
    } else null

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                stringResource(R.string.hv_12v_history_title), fontSize = 18.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable(onClick = onDismiss)
                            )
                            // Subtitle hint is dropped in split-screen where there's no room.
                            if (!isSplitScreen) {
                                VerticalDivider(
                                    modifier = Modifier.height(14.dp),
                                    thickness = 1.dp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    stringResource(R.string.battery_history_subtitle),
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        BrandNavigationBar {
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.back),
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Battery12vSummaryChip(stringResource(R.string.stat_latest), latest?.let { "%.2f V".format(it.battery12vVoltage) } ?: "—", Modifier.weight(1f))
                        Battery12vSummaryChip(stringResource(R.string.stat_min), min12v?.let { "%.2f V".format(it) } ?: "—", Modifier.weight(1f))
                        Battery12vSummaryChip(stringResource(R.string.stat_max), max12v?.let { "%.2f V".format(it) } ?: "—", Modifier.weight(1f))
                        Battery12vSummaryChip(stringResource(R.string.stat_delta), delta12v?.let { "%+.2f V".format(it) } ?: "—", Modifier.weight(1f))
                    }
                    Battery12vHistoryChart(
                        points = mergedPoints,
                        socSource = socSource,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun Battery12vSummaryChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun Battery12vHistoryChart(
    points: List<BatteryVoltageHistoryPoint>,
    socSource: SocSource,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.no_12v_history),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val nowMs = remember(points) { max(points.last().timestamp, System.currentTimeMillis()) }
    val historyWindowMs = 48L * 60L * 60L * 1000L
    val tickStepMs = 6L * 60L * 60L * 1000L
    val startMs = nowMs - historyWindowMs
    val minVoltage = 12.0
    val maxVoltage = 14.0
    val chartColor = BatteryBlue
    val socColor   = MotorViolet
    val chargeColor = RegenGreen.copy(alpha = 0.12f)
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.16f)
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    var touchPos by remember { mutableStateOf<Offset?>(null) }
    val strChargingSample = stringResource(R.string.legend_charging_sample)
    val strDriveSample = stringResource(R.string.legend_drive_sample)
    val strNow = stringResource(R.string.label_now)

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.chart_12v_soc_label), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Canvas(modifier = Modifier.size(width = 18.dp, height = 8.dp)) {
                        drawLine(chartColor, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), 4f, cap = StrokeCap.Round)
                    }
                    Text(stringResource(R.string.legend_12v), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Canvas(modifier = Modifier.size(width = 18.dp, height = 8.dp)) {
                        drawLine(socColor, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), 3f,
                            cap = StrokeCap.Round,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)))
                    }
                    Text(stringResource(R.string.legend_soc_short), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(points) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            touchPos = down.position
                            drag(down.id) { change -> touchPos = change.position }
                            touchPos = null
                        }
                    }
            ) {
                val padL = 54f
                val padR = 58f
                val padT = 18f
                val padB = 42f
                val chartW = size.width - padL - padR
                val chartH = size.height - padT - padB
                val nc = drawContext.canvas.nativeCanvas
                val labelPaint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    color = axisColor.toArgb()
                    textSize = 19f
                }

                fun xOf(timestamp: Long): Float =
                    (padL + ((timestamp - startMs).toDouble() / (nowMs - startMs).coerceAtLeast(1L).toDouble()).toFloat() * chartW)
                        .coerceIn(padL, padL + chartW)

                fun yOf(voltage: Double): Float =
                    padT + chartH - (((voltage - minVoltage) / (maxVoltage - minVoltage).coerceAtLeast(0.1)).toFloat() * chartH)

                fun yOfSoc(pct: Double): Float =
                    padT + chartH - (pct / 100.0).toFloat() * chartH

                listOf(14.0, 13.0, 12.0).forEachIndexed { index, value ->
                    val y = padT + chartH * index / 2f
                    drawLine(gridColor, Offset(padL, y), Offset(size.width - padR, y), 1f)
                    labelPaint.textAlign = android.graphics.Paint.Align.RIGHT
                    nc.drawText("%.1f".format(value), padL - 8f, y + 7f, labelPaint)
                }

                val socAxisPaint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    color = socColor.copy(alpha = 0.8f).toArgb()
                    textSize = 19f
                    textAlign = android.graphics.Paint.Align.LEFT
                }
                for (tick in listOf(33, 66, 100)) {
                    val y = yOfSoc(tick.toDouble())
                    if (y in padT..(padT + chartH)) {
                        nc.drawText("$tick%", size.width - padR + 6f, y + 7f, socAxisPaint)
                    }
                }

                val timeTicks = buildList {
                    var tick = startMs
                    while (tick <= nowMs) {
                        add(tick)
                        tick += tickStepMs
                    }
                    if (lastOrNull() != nowMs) add(nowMs)
                }
                labelPaint.textAlign = android.graphics.Paint.Align.CENTER
                timeTicks.forEach { tick ->
                    val x = xOf(tick)
                    drawLine(gridColor, Offset(x, padT), Offset(x, padT + chartH), 1f)
                    val label = if (tick == nowMs) {
                        timeFormatter.format(Date(tick))
                    } else {
                        "$strNow - ${((nowMs - tick) / 3_600_000L)}h"
                    }
                    nc.drawText(label, x, size.height - 10f, labelPaint)
                }

                val chargingSegments = points.filter { it.isChargingSample }
                chargingSegments.forEach { point ->
                    val x = xOf(point.timestamp)
                    drawRect(
                        color = chargeColor,
                        topLeft = Offset((x - 1.5f).coerceAtLeast(padL), padT),
                        size = androidx.compose.ui.geometry.Size(3f, chartH)
                    )
                }

                fun pointSoc(pt: BatteryVoltageHistoryPoint): Double =
                    if (socSource == SocSource.PANEL && pt.socPanel > 0) pt.socPanel.toDouble()
                    else pt.soc

                if (points.size == 1) {
                    val x = xOf(points.first().timestamp)
                    val y = yOf(points.first().battery12vVoltage)
                    drawCircle(chartColor, radius = 6f, center = Offset(x, y))
                } else {
                    val path = Path().apply {
                        moveTo(xOf(points.first().timestamp), yOf(points.first().battery12vVoltage))
                        points.drop(1).forEach { point ->
                            lineTo(xOf(point.timestamp), yOf(point.battery12vVoltage))
                        }
                    }
                    drawPath(
                        path = path,
                        color = chartColor,
                        style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                    val socPoints = points.filter { pointSoc(it) > 0.0 }
                    if (socPoints.size >= 2) {
                        val socPath = Path().apply {
                            moveTo(xOf(socPoints.first().timestamp), yOfSoc(pointSoc(socPoints.first())))
                            socPoints.drop(1).forEach { pt ->
                                lineTo(xOf(pt.timestamp), yOfSoc(pointSoc(pt)))
                            }
                        }
                        drawPath(
                            path = socPath,
                            color = socColor.copy(alpha = 0.85f),
                            style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 6f)))
                        )
                    }
                }

                drawRect(
                    color = gridColor,
                    topLeft = Offset(padL, padT),
                    size = androidx.compose.ui.geometry.Size(chartW, chartH),
                    style = Stroke(width = 1f)
                )

                touchPos?.let { tp ->
                    if (tp.x in padL..(padL + chartW)) {
                        val closest = points.minByOrNull { kotlin.math.abs(xOf(it.timestamp) - tp.x) } ?: return@let
                        val cx = xOf(closest.timestamp)
                        val cy = yOf(closest.battery12vVoltage)
                        drawCrosshair(
                            cx = cx,
                            cy = cy,
                            w = size.width,
                            padL = padL,
                            padR = padR,
                            padT = padT,
                            chartH = chartH,
                            line1 = "12V: ${"%.2f".format(closest.battery12vVoltage)} V${pointSoc(closest).takeIf { it > 0.0 }?.let { "  |  SoC: ${"%.1f".format(it)}%" } ?: ""}",
                            line2 = if (closest.isChargingSample) strChargingSample else strDriveSample,
                            line3 = timeFormatter.format(Date(closest.timestamp)),
                            accentColor = chartColor
                        )
                    }
                }
            }
        }
    }
}

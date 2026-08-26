package com.byd.tripstats.ui.screens.batterydegrad

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.material3.MaterialTheme
import com.byd.tripstats.ui.theme.AccelerationOrange
import com.byd.tripstats.ui.theme.BatteryBlue
import com.byd.tripstats.ui.viewmodel.DashboardViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.*

// ── Regression data class ─────────────────────────────────────────────────────

internal data class RegressionResult(
    val slope          : Double,  // SoH % per millisecond
    val intercept      : Double,
    val projectedAt80Ms: Long?    // null if trend is flat or going up
) {
    fun predict(ms: Double): Double = slope * ms + intercept
}

// ── Chart ─────────────────────────────────────────────────────────────────────

@Composable
internal fun DegradationChart(
    data       : List<DashboardViewModel.SohDataPoint>,
    regression : RegressionResult,
    modifier   : Modifier = Modifier
) {
    val lineColor       = BatteryBlue
    val dotFill         = BatteryBlue
    val dotGlow         = BatteryBlue.copy(alpha = 0.18f)
    val trendColor      = AccelerationOrange
    val projectedColor  = AccelerationOrange.copy(alpha = 0.45f)
    val gridColor       = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f)
    val textColor       = MaterialTheme.colorScheme.onSurface
    val fmt             = SimpleDateFormat("MM/yy", Locale.getDefault())

    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        val padL = 52f; val padR = 16f; val padT = 12f; val padB = 32f
        val chartW = w - padL - padR; val chartH = h - padT - padB

        // Y range: clamp to [floor-2, 100], minimum window of 6 %
        val minSoh  = data.minOf { it.avgSoh }
        val yMin    = (floor(minSoh / 2) * 2 - 2).coerceAtLeast(70.0)
        val yMax    = 100.0
        val yRange  = yMax - yMin

        // X range: first trip to projection end (or +2 years if trend is flat)
        val xMinMs  = data.first().timestamp.toDouble()
        val projMs  = regression.projectedAt80Ms
        val xMaxMs  = if (projMs != null && projMs > data.last().timestamp)
            (data.last().timestamp + (projMs - data.last().timestamp) * 0.3)
                .coerceAtMost(data.last().timestamp + 5 * 365.25 * 86_400_000.0)
        else
            data.last().timestamp + 365.25 * 86_400_000.0  // always show at least +1 year
        val xRange  = xMaxMs - xMinMs

        fun xOf(ms: Double) = (padL + ((ms - xMinMs) / xRange) * chartW).toFloat()
        fun yOf(soh: Double) = (padT + chartH * (1.0 - (soh - yMin) / yRange)).toFloat()

        val nc = drawContext.canvas.nativeCanvas

        // Y grid + labels
        val yStep = if (yRange <= 8) 1.0 else if (yRange <= 15) 2.0 else 5.0
        var yTick = ceil(yMin / yStep) * yStep
        while (yTick <= yMax + 0.01) {
            val y = yOf(yTick)
            drawLine(gridColor, Offset(padL, y), Offset(w - padR, y), 1f)
            nc.drawText(
                "%.0f%%".format(yTick),
                padL - 6f, y + 8f,
                android.graphics.Paint().apply {
                    color     = textColor.copy(alpha = 0.65f).toArgb()
                    textSize  = 22f
                    textAlign = android.graphics.Paint.Align.RIGHT
                    isAntiAlias = true
                }
            )
            yTick += yStep
        }

        // X labels — only drawn when SoH drops vs the previous point,
        // plus always the first point. This keeps the axis uncluttered:
        // a flat SoH period shows no intermediate dates (they'd all look
        // the same anyway), and a drop immediately flags when it happened.
        val xLabelPaint = android.graphics.Paint().apply {
            color     = textColor.copy(alpha = 0.65f).toArgb()
            textSize  = 20f
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
        data.forEachIndexed { i, pt ->
            val shouldLabel = i == 0 ||
                pt.avgSoh < data[i - 1].avgSoh   // SoH dropped — mark when
            if (shouldLabel) {
                nc.drawText(
                    fmt.format(Date(pt.timestamp)),
                    xOf(pt.timestamp.toDouble()),
                    h - 4f,
                    xLabelPaint
                )
            }
        }

        // ── Trend line (solid orange) ──────────────────────────────────────────
        if (data.size >= 2) {
            val trendPath = Path().apply {
                moveTo(xOf(xMinMs), yOf(regression.predict(xMinMs)).coerceIn(padT, padT + chartH))
                lineTo(xOf(data.last().timestamp.toDouble()),
                    yOf(regression.predict(data.last().timestamp.toDouble()))
                        .coerceIn(padT, padT + chartH))
            }
            drawPath(trendPath, trendColor,
                style = Stroke(width = 2.5f, cap = StrokeCap.Round))

            // ── Projected line (dashed, faded) ────────────────────────────────
            val projEnd = xMaxMs
            val projPath = Path().apply {
                moveTo(xOf(data.last().timestamp.toDouble()),
                    yOf(regression.predict(data.last().timestamp.toDouble()))
                        .coerceIn(padT, padT + chartH))
                lineTo(xOf(projEnd),
                    yOf(regression.predict(projEnd)).coerceIn(padT, padT + chartH))
            }
            drawPath(projPath, projectedColor,
                style = Stroke(width = 2f, cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))))
        }

        // ── Actual data line ───────────────────────────────────────────────────
        if (data.size >= 2) {
            val linePath = Path().apply {
                moveTo(xOf(data[0].timestamp.toDouble()), yOf(data[0].avgSoh))
                data.drop(1).forEach { pt ->
                    lineTo(xOf(pt.timestamp.toDouble()), yOf(pt.avgSoh))
                }
            }
            drawPath(linePath, lineColor,
                style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        // ── Data dots ─────────────────────────────────────────────────────────
        data.forEach { pt ->
            val x = xOf(pt.timestamp.toDouble())
            val y = yOf(pt.avgSoh)
            drawCircle(dotGlow, 14f, Offset(x, y))
            drawCircle(dotFill,  6f, Offset(x, y))
            drawCircle(Color.White, 2.5f, Offset(x, y))
        }
    }
}

// ── Regression helpers ────────────────────────────────────────────────────────

private fun linearRegression(data: List<DashboardViewModel.SohDataPoint>): RegressionResult {
    val xs  = data.map { it.timestamp.toDouble() }
    val ys  = data.map { it.avgSoh }
    val xMean = xs.average()
    val yMean = ys.average()
    val num   = xs.zip(ys).sumOf { (x, y) -> (x - xMean) * (y - yMean) }
    val den   = xs.sumOf { x -> (x - xMean).pow(2) }
    val slope = if (den == 0.0) 0.0 else num / den
    val intercept = yMean - slope * xMean

    // Project when SoH hits 80% — only meaningful if trend is declining
    val proj80Ms: Long? = if (slope < 0) {
        val ms = (80.0 - intercept) / slope
        if (ms > data.last().timestamp) ms.toLong() else null
    } else null

    return RegressionResult(slope, intercept, proj80Ms)
}

private data class DegradationStats(
    val declinePerYear   : Double,
    val projectedAt80Label: String
)

private fun degradationStats(
    regression: RegressionResult
): DegradationStats {
    // slope is % per ms → convert to % per year
    val msPerYear      = 365.25 * 24 * 3_600_000.0
    val declinePerYear = -regression.slope * msPerYear  // positive = declining

    val maxReasonableMs = System.currentTimeMillis() + 100L * 365 * 24 * 3600 * 1000

    val projLabel = when {
        regression.slope >= 0 -> "Not declining"
        regression.projectedAt80Ms == null -> "Far future"
        regression.projectedAt80Ms > maxReasonableMs -> "Far future"
        else -> {
            val calendar = java.util.Calendar.getInstance().apply {
                timeInMillis = regression.projectedAt80Ms
            }
            "${calendar.get(java.util.Calendar.YEAR)}"
        }
    }
    return DegradationStats(declinePerYear.coerceAtLeast(0.0), projLabel)
}

// ── Package-internal entry points for the screen ──────────────────────────────

internal fun computeLinearRegression(data: List<DashboardViewModel.SohDataPoint>): RegressionResult =
    linearRegression(data)

internal data class DegradationScreenStats(
    val declinePerYear    : Double,
    val projectedAt80Label: String
)

internal fun buildDegradationScreenStats(regression: RegressionResult): DegradationScreenStats {
    val stats = degradationStats(regression)
    return DegradationScreenStats(stats.declinePerYear, stats.projectedAt80Label)
}

package com.byd.tripstats.ui.components.heatmaps

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.byd.tripstats.R
import com.byd.tripstats.data.local.entity.TripDataPointEntity

@Composable
internal fun GradientVsConsumptionHeatmap(
    dataPoints: List<TripDataPointEntity>,
    useImperial: Boolean = false,
    modifier: Modifier = Modifier
) {
    val cf    = if (useImperial) 1f / KM_TO_MI else 1f
    val xBins = 20; val yBins = 20
    val xMin  = -10f; val xMax = 10f
    val yMin  = -30f * cf; val yMax = 80f * cf

    val gradPoints = remember(dataPoints, useImperial) {
        dataPoints.zipWithNext { a, b ->
            val dtSec = (b.timestamp - a.timestamp) / 1000.0
            if (dtSec < 0.5 || dtSec > 30.0) return@zipWithNext null
            if (a.speed < 5.0) return@zipWithNext null
            val distanceKm = segmentDistanceKm(a, b, dtSec)
            if (distanceKm < 0.001f) return@zipWithNext null
            val dAlt     = (b.altitude - a.altitude).toFloat()
            val gradient = (dAlt / (distanceKm * 1000f)) * 100f
            val cons     = ((a.power / a.speed * 100.0) * cf).toFloat()
            if (gradient < xMin || gradient > xMax) return@zipWithNext null
            if (cons     < yMin || cons     > yMax) return@zipWithNext null
            gradient to cons
        }.filterNotNull()
    }

    if (gradPoints.size < 10) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.heatmap_no_altitude),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val cells = remember(gradPoints) {
        buildGrid(gradPoints, xMin, xMax, xBins, yMin, yMax, yBins)
    }

    Heatmap2D(
        cells      = cells,
        xLabels    = axisLabels(xMin, xMax, xBins, "%.0f"),
        yLabels    = axisLabels(yMin, yMax, yBins),
        xAxisLabel = stringResource(R.string.heatmap_axis_gradient),
        yAxisLabel = if (useImperial) "kWh / 100 mi" else "kWh / 100 km",
        xMin = xMin, xMax = xMax, yMin = yMin, yMax = yMax,
        modifier   = modifier
    )
}

@Composable
internal fun FrontVsRearRpmHeatmap(
    dataPoints: List<TripDataPointEntity>,
    modifier: Modifier = Modifier
) {
    val xBins = 14; val yBins = 14
    val xMin  = 0f; val xMax = 14000f
    val yMin  = 0f; val yMax = 14000f

    val rpmPoints = remember(dataPoints) {
        dataPoints.mapNotNull { dp ->
            val front = dp.engineSpeedFront.toFloat()
            val rear  = dp.engineSpeedRear.toFloat()
            if (front <= 0f && rear <= 0f) return@mapNotNull null
            front to rear
        }
    }

    if (rpmPoints.size < 10) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.heatmap_no_dual_motor),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val cells = remember(rpmPoints) {
        buildGrid(rpmPoints, xMin, xMax, xBins, yMin, yMax, yBins)
    }

    Heatmap2D(
        cells      = cells,
        xLabels    = axisLabels(xMin, xMax, xBins, transform = ::fmtRpm),
        yLabels    = axisLabels(yMin, yMax, yBins, transform = ::fmtRpm),
        xAxisLabel = stringResource(R.string.heatmap_axis_front_rpm),
        yAxisLabel = stringResource(R.string.heatmap_axis_rear_rpm),
        xMin = xMin, xMax = xMax, yMin = yMin, yMax = yMax,
        xValueFmt  = ::fmtRpm,
        yValueFmt  = ::fmtRpm,
        modifier   = modifier
    )
}

@Composable
internal fun TyrePressureVsConsumptionHeatmap(
    dataPoints: List<TripDataPointEntity>,
    useImperial: Boolean = false,
    modifier: Modifier = Modifier
) {
    val cf    = if (useImperial) 1f / KM_TO_MI else 1f
    val xBins = 16; val yBins = 14
    val xMin  = 28f; val xMax = 46f
    val yMin  =  0f; val yMax = 60f * cf

    val points = remember(dataPoints, useImperial) {
        dataPoints.mapNotNull { p ->
            val spd  = p.speed.toFloat().takeIf { it > 10f } ?: return@mapNotNull null
            val pwr  = p.power.toFloat().takeIf { it > 0f }  ?: return@mapNotNull null
            val cons = (pwr / spd * 100f) * cf
            if (cons > yMax) return@mapNotNull null
            val lf = p.tyrePressureLF.toFloat()
            val rf = p.tyrePressureRF.toFloat()
            val lr = p.tyrePressureLR.toFloat()
            val rr = p.tyrePressureRR.toFloat()
            if (lf == 0f && rf == 0f && lr == 0f && rr == 0f) return@mapNotNull null
            val avgPsi = (lf + rf + lr + rr) / 4f
            avgPsi to cons
        }
    }

    if (points.size < 10) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.chart_no_tyre_data),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val cells = remember(points) { buildGrid(points, xMin, xMax, xBins, yMin, yMax, yBins) }

    Heatmap2D(
        cells      = cells,
        xLabels    = axisLabels(xMin, xMax, xBins, "%.0f"),
        yLabels    = axisLabels(yMin, yMax, yBins),
        xAxisLabel = stringResource(R.string.heatmap_axis_avg_tyre_pressure),
        yAxisLabel = if (useImperial) "kWh / 100 mi" else "kWh / 100 km",
        xMin = xMin, xMax = xMax, yMin = yMin, yMax = yMax,
        modifier   = modifier
    )
}

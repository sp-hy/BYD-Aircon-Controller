package com.byd.tripstats.ui.components.heatmaps

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.byd.tripstats.R
import com.byd.tripstats.data.config.Drivetrain
import com.byd.tripstats.data.local.entity.TripDataPointEntity
import kotlin.math.abs
import kotlin.math.max

@Composable
internal fun PowerVsSpeedHeatmap(
    dataPoints: List<TripDataPointEntity>,
    useImperial: Boolean = false,
    modifier: Modifier = Modifier
) {
    val sf    = if (useImperial) KM_TO_MI else 1f
    val xMin  = 0f;    val xMax = 160f * sf
    val yMin  = -100f; val yMax = 300f
    val xBins = 16; val yBins = 14

    val cells = remember(dataPoints, useImperial) {
        buildGrid(
            points = dataPoints.mapNotNull { p ->
                val spd = (p.speed * sf).toFloat().takeIf { it >= 0f } ?: return@mapNotNull null
                val pwr = p.power.toFloat()
                spd to pwr
            },
            xMin = xMin, xMax = xMax, xBins = xBins,
            yMin = yMin, yMax = yMax, yBins = yBins
        )
    }

    Heatmap2D(
        cells      = cells,
        xLabels    = axisLabels(xMin, xMax, xBins),
        yLabels    = axisLabels(yMin, yMax, yBins),
        xAxisLabel = stringResource(R.string.heatmap_axis_speed, if (useImperial) "mph" else "km/h"),
        yAxisLabel = stringResource(R.string.heatmap_axis_power),
        xMin = xMin, xMax = xMax, yMin = yMin, yMax = yMax,
        modifier   = modifier
    )
}

@Composable
internal fun ConsumptionVsSpeedHeatmap(
    dataPoints: List<TripDataPointEntity>,
    useImperial: Boolean = false,
    modifier: Modifier = Modifier
) {
    val sf    = if (useImperial) KM_TO_MI else 1f
    val cf    = if (useImperial) 1f / KM_TO_MI else 1f
    val xBins = 14; val yBins = 14
    val xMin  = 5f * sf;   val xMax = 160f * sf
    val yMin  = -60f * cf; val yMax = 80f * cf

    val cells = remember(dataPoints, useImperial) {
        buildGrid(
            points = dataPoints.mapNotNull { p ->
                val spd = p.speed.toFloat().takeIf { it >= 5f } ?: return@mapNotNull null
                val pwr = p.power.toFloat()
                val consumption = (pwr / spd * 100f) * cf
                (spd * sf) to consumption
            },
            xMin = xMin, xMax = xMax, xBins = xBins,
            yMin = yMin, yMax = yMax, yBins = yBins
        )
    }

    Heatmap2D(
        cells      = cells,
        xLabels    = axisLabels(xMin, xMax, xBins),
        yLabels    = axisLabels(yMin, yMax, yBins),
        xAxisLabel = stringResource(R.string.heatmap_axis_speed, if (useImperial) "mph" else "km/h"),
        yAxisLabel = if (useImperial) "kWh / 100 mi" else "kWh / 100 km",
        xMin = xMin, xMax = xMax, yMin = yMin, yMax = yMax,
        modifier   = modifier
    )
}

@Composable
internal fun RegenVsSpeedHeatmap(
    dataPoints: List<TripDataPointEntity>,
    useImperial: Boolean = false,
    modifier: Modifier = Modifier
) {
    val sf    = if (useImperial) KM_TO_MI else 1f
    val xBins = 14; val yBins = 12
    val xMin  = 0f; val xMax = 140f * sf
    val yMin  = 0f; val yMax = 120f

    val regenPoints = remember(dataPoints, useImperial) {
        dataPoints.mapNotNull { p ->
            val spd = (p.speed * sf).toFloat().takeIf { it >= 0f } ?: return@mapNotNull null
            val pwr = p.power.toFloat().takeIf { it < 0f } ?: return@mapNotNull null
            spd to abs(pwr)
        }
    }

    if (regenPoints.size < 10) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.heatmap_no_regen),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val cells = remember(regenPoints) {
        buildGrid(regenPoints, xMin, xMax, xBins, yMin, yMax, yBins)
    }

    Heatmap2D(
        cells      = cells,
        xLabels    = axisLabels(xMin, xMax, xBins),
        yLabels    = axisLabels(yMin, yMax, yBins),
        xAxisLabel = stringResource(R.string.heatmap_axis_speed, if (useImperial) "mph" else "km/h"),
        yAxisLabel = stringResource(R.string.heatmap_axis_regen_power),
        xMin = xMin, xMax = xMax, yMin = yMin, yMax = yMax,
        modifier   = modifier
    )
}

@Composable
internal fun RpmVsSpeedHeatmap(
    dataPoints: List<TripDataPointEntity>,
    drivetrain: Drivetrain,
    useImperial: Boolean = false,
    modifier: Modifier = Modifier
) {
    val sf    = if (useImperial) KM_TO_MI else 1f
    val xBins = 14; val yBins = 12
    val xMin  = 0f;    val xMax = 160f * sf
    val yMin  = 0f;    val yMax = 14000f

    val rpmPoints = remember(dataPoints, drivetrain, useImperial) {
        dataPoints.mapNotNull { p ->
            val spd = (p.speed * sf).toFloat().takeIf { it >= 0f } ?: return@mapNotNull null
            val rpm = when (drivetrain) {
                Drivetrain.FWD -> p.engineSpeedFront.toFloat()
                Drivetrain.RWD -> p.engineSpeedRear.toFloat()
                Drivetrain.AWD -> max(p.engineSpeedFront.toFloat(), p.engineSpeedRear.toFloat())
            }.takeIf { it > 0f } ?: return@mapNotNull null
            spd to rpm
        }
    }

    if (rpmPoints.size < 10) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.chart_no_motor_data),
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
        xLabels    = axisLabels(xMin, xMax, xBins),
        yLabels    = axisLabels(yMin, yMax, yBins, transform = ::fmtRpm),
        xAxisLabel = stringResource(R.string.heatmap_axis_speed, if (useImperial) "mph" else "km/h"),
        yAxisLabel = when (drivetrain) {
            Drivetrain.FWD -> stringResource(R.string.heatmap_axis_front_motor_rpm)
            Drivetrain.RWD -> stringResource(R.string.heatmap_axis_rear_motor_rpm)
            Drivetrain.AWD -> stringResource(R.string.heatmap_axis_motor_rpm)
        },
        xMin = xMin, xMax = xMax, yMin = yMin, yMax = yMax,
        yValueFmt  = ::fmtRpm,
        modifier   = modifier
    )
}

@Composable
internal fun AccelerationVsSpeedHeatmap(
    dataPoints: List<TripDataPointEntity>,
    useImperial: Boolean = false,
    modifier: Modifier = Modifier
) {
    val sf    = if (useImperial) KM_TO_MI else 1f
    val xBins = 16; val yBins = 16
    val xMin  = 0f;       val xMax = 160f * sf
    val yMin  = -8f * sf; val yMax = 8f * sf

    val accelPoints = remember(dataPoints, useImperial) {
        dataPoints.zipWithNext { a, b ->
            val dtSec = (b.timestamp - a.timestamp) / 1000.0
            if (dtSec < 0.5 || dtSec > 30.0) return@zipWithNext null
            val accel    = (((b.speed - a.speed) / dtSec) * sf).toFloat()
            val midSpeed = (((a.speed + b.speed) / 2.0) * sf).toFloat()
            midSpeed to accel
        }.filterNotNull()
    }

    if (accelPoints.size < 10) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.heatmap_no_speed_transitions),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val cells = remember(accelPoints) {
        buildGrid(accelPoints, xMin, xMax, xBins, yMin, yMax, yBins)
    }

    Heatmap2D(
        cells      = cells,
        xLabels    = axisLabels(xMin, xMax, xBins),
        yLabels    = axisLabels(yMin, yMax, yBins, "%.1f"),
        xAxisLabel = stringResource(R.string.heatmap_axis_speed, if (useImperial) "mph" else "km/h"),
        yAxisLabel = stringResource(R.string.heatmap_axis_accel, if (useImperial) "mph/s" else "km/h/s"),
        xMin = xMin, xMax = xMax, yMin = yMin, yMax = yMax,
        yValueFmt  = { "%.1f".format(it) },
        modifier   = modifier
    )
}

@Composable
internal fun TimeOfDayVsSpeedHeatmap(
    dataPoints: List<TripDataPointEntity>,
    useImperial: Boolean = false,
    modifier: Modifier = Modifier
) {
    val sf    = if (useImperial) KM_TO_MI else 1f
    val xBins = 24; val yBins = 16
    val xMin  = 0f;  val xMax = 24f
    val yMin  = 0f;  val yMax = 160f * sf

    val timePoints = remember(dataPoints, useImperial) {
        val cal = java.util.Calendar.getInstance()
        dataPoints.mapNotNull { dp ->
            if (dp.speed <= 0.0) return@mapNotNull null
            cal.timeInMillis = dp.timestamp
            val hour = cal.get(java.util.Calendar.HOUR_OF_DAY).toFloat()
            hour to (dp.speed * sf).toFloat()
        }
    }

    if (timePoints.size < 10) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.heatmap_no_time_of_day),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val cells = remember(timePoints) {
        buildGrid(timePoints, xMin, xMax, xBins, yMin, yMax, yBins)
    }

    Heatmap2D(
        cells      = cells,
        xLabels    = axisLabels(xMin, xMax, xBins),
        yLabels    = axisLabels(yMin, yMax, yBins),
        xAxisLabel = stringResource(R.string.heatmap_axis_hour_of_day),
        yAxisLabel = stringResource(R.string.heatmap_axis_speed, if (useImperial) "mph" else "km/h"),
        xMin = xMin, xMax = xMax, yMin = yMin, yMax = yMax,
        modifier   = modifier
    )
}

@Composable
internal fun SpeedVsBatteryTempHeatmap(
    dataPoints: List<TripDataPointEntity>,
    useImperial: Boolean = false,
    modifier: Modifier = Modifier
) {
    val sf    = if (useImperial) KM_TO_MI else 1f
    val xBins = 16; val yBins = 14
    val xMin  = 0f;  val xMax = 160f * sf
    val yMin  = 10f; val yMax = 50f

    val points = remember(dataPoints, useImperial) {
        dataPoints.mapNotNull { p ->
            val temp = p.batteryTemp.toFloat().takeIf { it > 0f } ?: return@mapNotNull null
            (p.speed * sf).toFloat() to temp
        }
    }

    if (points.size < 10) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.heatmap_no_battery_temp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val cells = remember(points) { buildGrid(points, xMin, xMax, xBins, yMin, yMax, yBins) }

    Heatmap2D(
        cells      = cells,
        xLabels    = axisLabels(xMin, xMax, xBins),
        yLabels    = axisLabels(yMin, yMax, yBins),
        xAxisLabel = stringResource(R.string.heatmap_axis_speed, if (useImperial) "mph" else "km/h"),
        yAxisLabel = stringResource(R.string.heatmap_axis_battery_temp),
        xMin = xMin, xMax = xMax, yMin = yMin, yMax = yMax,
        modifier   = modifier
    )
}

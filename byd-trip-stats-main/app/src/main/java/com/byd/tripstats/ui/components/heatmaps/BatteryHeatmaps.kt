package com.byd.tripstats.ui.components.heatmaps

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.byd.tripstats.R
import com.byd.tripstats.data.local.entity.TripDataPointEntity
import com.byd.tripstats.data.preferences.SocSource
import kotlin.math.abs

@Composable
internal fun BatteryTempVsPowerHeatmap(
    dataPoints: List<TripDataPointEntity>,
    modifier: Modifier = Modifier
) {
    val xBins = 12; val yBins = 12
    val xMin  = 10f;   val xMax = 50f
    val yMin  = -100f; val yMax = 300f

    val tempPoints = remember(dataPoints) {
        dataPoints.mapNotNull { p ->
            val temp = p.batteryTemp.toFloat().takeIf { it > 0f } ?: return@mapNotNull null
            val pwr  = p.power.toFloat()
            temp to pwr
        }
    }

    if (tempPoints.size < 10) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.heatmap_no_battery_temp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val cells = remember(tempPoints) {
        buildGrid(tempPoints, xMin, xMax, xBins, yMin, yMax, yBins)
    }

    Heatmap2D(
        cells      = cells,
        xLabels    = axisLabels(xMin, xMax, xBins),
        yLabels    = axisLabels(yMin, yMax, yBins),
        xAxisLabel = stringResource(R.string.heatmap_axis_battery_temp),
        yAxisLabel = stringResource(R.string.heatmap_axis_power),
        xMin = xMin, xMax = xMax, yMin = yMin, yMax = yMax,
        modifier   = modifier
    )
}

@Composable
internal fun SocVsConsumptionHeatmap(
    dataPoints : List<TripDataPointEntity>,
    useImperial: Boolean = false,
    socSource  : SocSource = SocSource.PANEL,
    modifier   : Modifier = Modifier
) {
    val cf    = if (useImperial) 1f / KM_TO_MI else 1f
    val xBins = 20; val yBins = 16
    val xMin  = 0f;  val xMax = 100f
    val yMin  = 0f;  val yMax = 80f * cf

    val consPoints = remember(dataPoints, useImperial, socSource) {
        dataPoints.mapNotNull { p ->
            val spd = p.speed.toFloat().takeIf { it > 10f } ?: return@mapNotNull null
            val pwr = p.power.toFloat().takeIf { it > 0f } ?: return@mapNotNull null
            val cons = (pwr / spd * 100f) * cf
            if (cons > yMax) return@mapNotNull null
            val soc = if (socSource == SocSource.PANEL && p.socPanel > 0) p.socPanel.toFloat() else p.soc.toFloat()
            soc to cons
        }
    }

    if (consPoints.size < 10) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.heatmap_no_samples),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val cells = remember(consPoints) {
        buildGrid(consPoints, xMin, xMax, xBins, yMin, yMax, yBins)
    }

    Heatmap2D(
        cells      = cells,
        xLabels    = axisLabels(xMin, xMax, xBins),
        yLabels    = axisLabels(yMin, yMax, yBins),
        xAxisLabel = "SOC (%)",
        yAxisLabel = if (useImperial) "kWh / 100 mi" else "kWh / 100 km",
        xMin = xMin, xMax = xMax, yMin = yMin, yMax = yMax,
        modifier   = modifier
    )
}

@Composable
internal fun SocVsRegenHeatmap(
    dataPoints: List<TripDataPointEntity>,
    socSource : SocSource = SocSource.PANEL,
    modifier  : Modifier = Modifier
) {
    val xBins = 20; val yBins = 14
    val xMin  =  0f; val xMax = 100f
    val yMin  =  0f; val yMax = 100f

    val points = remember(dataPoints, socSource) {
        dataPoints.mapNotNull { p ->
            val pwr = p.power.toFloat().takeIf { it < -1f } ?: return@mapNotNull null
            val soc = if (socSource == SocSource.PANEL && p.socPanel > 0) p.socPanel.toFloat() else p.soc.toFloat()
            soc to abs(pwr)
        }
    }

    if (points.size < 10) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.heatmap_no_regen),
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
        xAxisLabel = "SOC (%)",
        yAxisLabel = stringResource(R.string.heatmap_axis_regen_power),
        xMin = xMin, xMax = xMax, yMin = yMin, yMax = yMax,
        modifier   = modifier
    )
}

@Composable
internal fun CellVoltageSpreadVsSocHeatmap(
    dataPoints: List<TripDataPointEntity>,
    socSource : SocSource = SocSource.PANEL,
    modifier  : Modifier = Modifier
) {
    val xBins = 20; val yBins = 16
    val xMin  =  0f;    val xMax = 100f
    val yMin  =  0f;    val yMax =   0.1f

    val points = remember(dataPoints, socSource) {
        dataPoints.mapNotNull { p ->
            val vMax = p.batteryCellVoltageMax.toFloat()
            val vMin = p.batteryCellVoltageMin.toFloat()
            if (vMax == 0f && vMin == 0f) return@mapNotNull null
            val spread = (vMax - vMin).coerceAtLeast(0f)
            val soc = if (socSource == SocSource.PANEL && p.socPanel > 0) p.socPanel.toFloat() else p.soc.toFloat()
            soc to spread
        }
    }

    if (points.size < 10) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.heatmap_no_cell_voltage),
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
        yLabels    = axisLabels(yMin, yMax, yBins, "%.3f"),
        xAxisLabel = "SOC (%)",
        yAxisLabel = stringResource(R.string.heatmap_axis_cell_spread),
        xMin = xMin, xMax = xMax, yMin = yMin, yMax = yMax,
        yValueFmt  = { "%.3f".format(it) },
        yTickWidth = 70f,
        modifier   = modifier
    )
}

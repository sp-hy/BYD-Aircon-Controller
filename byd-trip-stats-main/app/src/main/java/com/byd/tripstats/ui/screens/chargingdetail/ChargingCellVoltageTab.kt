package com.byd.tripstats.ui.screens.chargingdetail

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.byd.tripstats.R
import com.byd.tripstats.data.preferences.SocSource
import com.byd.tripstats.ui.theme.AccelerationOrange
import com.byd.tripstats.ui.theme.RegenGreen

/**
 * Per-cell high/low voltage across a charge, with the high−low spread (cell imbalance) on the
 * crosshair. Cell min/max can be absent early on some firmwares (only the aggregate is reported at
 * first) — [ChargingSeriesChart]'s per-series gap-skip handles that. X-axis toggles time / SoC.
 */
@Composable
internal fun ChargingCellVoltageTab(
    dataPoints  : List<ChargingChartPoint>,
    isSynthetic : Boolean = false,
    socSource   : SocSource = SocSource.PANEL,
    xAxisMode   : ChargingXAxisMode,
    onXAxisModeChange: (ChargingXAxisMode) -> Unit,
) {
    val cellVoltageTitle = stringResource(R.string.charging_chart_cell_voltage)
    val strCellLow  = stringResource(R.string.cell_low_label)
    val strCellHigh = stringResource(R.string.cell_high_label)
    val strXHigh    = stringResource(R.string.crosshair_cell_high)
    val strXLow     = stringResource(R.string.crosshair_cell_low)
    ChargingSeriesChart(
        dataPoints  = dataPoints,
        isSynthetic = isSynthetic,
        title       = cellVoltageTitle,
        yAxisLabel  = "V",
        socSource   = socSource,
        xAxisMode   = xAxisMode,
        onXAxisModeChange = onXAxisModeChange,
        series = listOf(
            ChargingSeriesSpec(strCellLow,  RegenGreen,         2.5f) { it.batteryCellVoltageMinV },
            ChargingSeriesSpec(strCellHigh, AccelerationOrange, 2.5f) { it.batteryCellVoltageMaxV },
        ),
        crosshairFor = { p ->
            val hi = p.batteryCellVoltageMaxV
            val lo = p.batteryCellVoltageMinV
            if (hi > 0.0 && lo > 0.0) {
                ChargingCrosshairInfo(
                    anchor = hi,
                    lines = listOf(
                        strXHigh.format(hi),
                        strXLow.format(lo),
                        "Δ %.0f mV".format((hi - lo) * 1000),
                    ),
                    color = AccelerationOrange,
                )
            } else null
        },
    )
}

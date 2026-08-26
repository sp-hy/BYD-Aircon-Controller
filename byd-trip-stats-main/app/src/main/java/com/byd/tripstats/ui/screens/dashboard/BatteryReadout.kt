package com.byd.tripstats.ui.screens.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.byd.tripstats.R
import com.byd.tripstats.data.preferences.SocSource

/**
 * What the live dashboard battery tile is showing. A tap toggles the tile between the SoC the user
 * picked in Settings → Preferences (Panel or BMS) and the BMS remaining-EV energy — a dashboard-only
 * view flag ([PreferencesManager.dashboardShowRemainingKwh]). The Panel/BMS choice itself is owned by
 * Settings and the app-wide [SocSource]; tapping the tile never changes it, so charts/history and the
 * setting are untouched. Tapping again returns to whichever SoC source is currently selected.
 */
enum class BatteryReadoutMode { SOC_PANEL, SOC_BMS, REMAINING_KWH }

fun batteryReadoutMode(socSource: SocSource, showRemainingKwh: Boolean): BatteryReadoutMode = when {
    showRemainingKwh -> BatteryReadoutMode.REMAINING_KWH
    socSource == SocSource.PANEL -> BatteryReadoutMode.SOC_PANEL
    else -> BatteryReadoutMode.SOC_BMS
}

@Composable
fun batteryReadoutLabel(mode: BatteryReadoutMode): String = when (mode) {
    BatteryReadoutMode.SOC_PANEL -> stringResource(R.string.stat_soc_panel)
    BatteryReadoutMode.SOC_BMS -> stringResource(R.string.stat_soc_bms)
    BatteryReadoutMode.REMAINING_KWH -> stringResource(R.string.stat_remaining_kwh)
}

/**
 * `(value, unit)` for the readout. [remainingKwh] is the BMS remaining-EV energy
 * (`powerBatteryRemainPowerEV`); an em dash is shown when it isn't reported — which is itself the
 * signal that the usable-kWh feed is absent on this hardware.
 */
fun batteryReadoutValueUnit(
    mode: BatteryReadoutMode,
    socPanel: Int,
    socBms: Double,
    remainingKwh: Double?,
): Pair<String, String> = when (mode) {
    BatteryReadoutMode.SOC_PANEL -> "$socPanel" to "%"
    BatteryReadoutMode.SOC_BMS -> "%.1f".format(socBms) to "%"
    // "kWh" unit keeps the tile uniform with the others; the label is just "Remaining".
    BatteryReadoutMode.REMAINING_KWH -> (remainingKwh?.let { "%.1f".format(it) } ?: "—") to "kWh"
}

package com.byd.tripstats.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.byd.tripstats.R
import com.byd.tripstats.data.config.Drivetrain
import com.byd.tripstats.data.local.entity.TripDataPointEntity
import com.byd.tripstats.data.preferences.PreferencesManager
import com.byd.tripstats.data.preferences.isImperial
import com.byd.tripstats.ui.components.heatmaps.*

@Composable
fun TripHeatmapsTab(dataPoints: List<TripDataPointEntity>) {
    if (dataPoints.size < 30) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.heatmap_no_data),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { PreferencesManager(context.applicationContext) }
    val selectedCar by prefs.selectedCarConfig.collectAsState(initial = null)
    val car = selectedCar ?: return
    val unitSystem by prefs.unitSystem.collectAsState(initial = prefs.getCachedUnitSystem())
    val useImperial = unitSystem.isImperial
    val socSource by prefs.socSource.collectAsState(initial = prefs.getCachedSocSource())
    var selectedDriveMode by remember { mutableStateOf(DriveModeFilter.ALL) }
    var selectedRegenMode by remember { mutableStateOf(RegenModeFilter.ALL) }
    val filteredDataPoints = remember(dataPoints, selectedDriveMode, selectedRegenMode) {
        filterTripPointsByModes(dataPoints, selectedDriveMode, selectedRegenMode)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        if (hasTripModeData(dataPoints)) {
            Text(
                text = stringResource(R.string.heatmap_mode_filters),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            ModeFilterRow(
                title = stringResource(R.string.mode_drive_label),
                filters = DriveModeFilter.entries,
                selected = selectedDriveMode,
                onSelected = { selectedDriveMode = it }
            )
            ModeFilterRow(
                title = stringResource(R.string.mode_regen_label),
                filters = RegenModeFilter.entries,
                selected = selectedRegenMode,
                onSelected = { selectedRegenMode = it }
            )
        }

        val consUnit  = if (useImperial) "kWh/100mi" else "kWh/100km"

        HeatmapCard(
            title    = stringResource(R.string.heatmap_power_vs_speed),
            subtitle = stringResource(R.string.heatmap_power_vs_speed_desc)
        ) {
            PowerVsSpeedHeatmap(filteredDataPoints, useImperial, Modifier.fillMaxSize())
        }

        HeatmapCard(
            title    = stringResource(R.string.heatmap_consumption_vs_speed),
            subtitle = stringResource(R.string.heatmap_consumption_vs_speed_desc, consUnit)
        ) {
            ConsumptionVsSpeedHeatmap(filteredDataPoints, useImperial, Modifier.fillMaxSize())
        }

        HeatmapCard(
            title    = stringResource(R.string.heatmap_regen_vs_speed),
            subtitle = stringResource(R.string.heatmap_regen_vs_speed_desc)
        ) {
            RegenVsSpeedHeatmap(filteredDataPoints, useImperial, Modifier.fillMaxSize())
        }

        HeatmapCard(
            title = when (car.drivetrain) {
                Drivetrain.FWD -> stringResource(R.string.heatmap_front_rpm_vs_speed)
                Drivetrain.RWD -> stringResource(R.string.heatmap_rear_rpm_vs_speed)
                Drivetrain.AWD -> stringResource(R.string.heatmap_rpm_vs_speed)
            },
            subtitle = when (car.drivetrain) {
                Drivetrain.FWD -> stringResource(R.string.heatmap_front_rpm_desc)
                Drivetrain.RWD -> stringResource(R.string.heatmap_rear_rpm_desc)
                Drivetrain.AWD -> stringResource(R.string.heatmap_rpm_desc)
            }
        ) {
            RpmVsSpeedHeatmap(
                dataPoints  = filteredDataPoints,
                drivetrain  = car.drivetrain,
                useImperial = useImperial,
                modifier    = Modifier.fillMaxSize()
            )
        }

        HeatmapCard(
            title    = stringResource(R.string.heatmap_temp_vs_power),
            subtitle = stringResource(R.string.heatmap_temp_vs_power_desc)
        ) {
            BatteryTempVsPowerHeatmap(filteredDataPoints, Modifier.fillMaxSize())
        }

        HeatmapCard(
            title    = stringResource(R.string.heatmap_accel_vs_speed),
            subtitle = stringResource(R.string.heatmap_accel_desc)
        ) {
            AccelerationVsSpeedHeatmap(filteredDataPoints, useImperial, Modifier.fillMaxSize())
        }

        HeatmapCard(
            title    = stringResource(R.string.heatmap_soc_vs_consumption),
            subtitle = stringResource(R.string.heatmap_soc_consumption_desc)
        ) {
            SocVsConsumptionHeatmap(filteredDataPoints, useImperial, socSource, Modifier.fillMaxSize())
        }

        HeatmapCard(
            title    = stringResource(R.string.heatmap_time_vs_speed),
            subtitle = stringResource(R.string.heatmap_time_speed_desc)
        ) {
            TimeOfDayVsSpeedHeatmap(filteredDataPoints, useImperial, Modifier.fillMaxSize())
        }

        HeatmapCard(
            title    = stringResource(R.string.heatmap_gradient_vs_consumption),
            subtitle = stringResource(R.string.heatmap_gradient_desc)
        ) {
            GradientVsConsumptionHeatmap(filteredDataPoints, useImperial, Modifier.fillMaxSize())
        }

        if (car.drivetrain == Drivetrain.AWD) {
            HeatmapCard(
                title    = stringResource(R.string.heatmap_front_vs_rear_rpm),
                subtitle = stringResource(R.string.heatmap_awd_desc)
            ) {
                FrontVsRearRpmHeatmap(filteredDataPoints, Modifier.fillMaxSize())
            }
        }

        HeatmapCard(
            title    = stringResource(R.string.heatmap_tyre_vs_consumption),
            subtitle = stringResource(R.string.heatmap_tyre_desc)
        ) {
            TyrePressureVsConsumptionHeatmap(filteredDataPoints, useImperial, Modifier.fillMaxSize())
        }

        HeatmapCard(
            title    = stringResource(R.string.heatmap_soc_vs_regen),
            subtitle = stringResource(R.string.heatmap_soc_regen_desc)
        ) {
            SocVsRegenHeatmap(filteredDataPoints, socSource, Modifier.fillMaxSize())
        }

        HeatmapCard(
            title    = stringResource(R.string.heatmap_speed_vs_temp),
            subtitle = stringResource(R.string.heatmap_speed_temp_desc)
        ) {
            SpeedVsBatteryTempHeatmap(filteredDataPoints, useImperial, Modifier.fillMaxSize())
        }

        HeatmapCard(
            title    = stringResource(R.string.heatmap_cell_spread_vs_soc),
            subtitle = stringResource(R.string.heatmap_cell_spread_desc)
        ) {
            CellVoltageSpreadVsSocHeatmap(filteredDataPoints, socSource, Modifier.fillMaxSize())
        }
    }
}

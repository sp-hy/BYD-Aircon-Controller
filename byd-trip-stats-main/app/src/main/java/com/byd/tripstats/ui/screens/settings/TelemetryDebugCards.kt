package com.byd.tripstats.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.byd.tripstats.R
import com.byd.tripstats.data.model.VehicleTelemetry
import com.byd.tripstats.sdk.VehicleTelemetrySnapshot
import com.byd.tripstats.ui.theme.BydElectricAzure

@Composable
internal fun DirectBydTelemetryCard(
    snapshot: VehicleTelemetrySnapshot?,
    onRefresh: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Filled.SettingsRemote, null, tint = BydElectricAzure, modifier = Modifier.size(22.dp))
                Text(stringResource(R.string.direct_byd_label), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            if (snapshot == null) {
                Text(
                    stringResource(R.string.waiting_direct_probes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                SettingsGroupLabel(stringResource(R.string.remote_only_label))
                SettingsDetailRow(
                    "Battery remain",
                    snapshot.powerBatteryRemainPowerEV?.let { "%.1f kWh".format(it) } ?: "n/a"
                )
                SettingsDetailRow(
                    "Last 50 km",
                    snapshot.instrumentLast50KmPowerConsume?.let { "%.1f kWh".format(it) } ?: "n/a"
                )
                SettingsDetailRow(
                    "Outside temp",
                    snapshot.instrumentOutCarTemperature?.let { "$it°C" } ?: "n/a"
                )
                SettingsDetailRow(
                    "VIN",
                    snapshot.bodyworkAutoVin ?: "n/a"
                )
                SettingsDetailRow(
                    "Seatbelt",
                    "D=${directIntOrNA(snapshot.instrumentSafetyBeltDriverStatus)}, " +
                        "P=${directIntOrNA(snapshot.instrumentSafetyBeltPassengerStatus)}"
                )
                HorizontalDivider()
                SettingsGroupLabel("Minor-drive")
                SettingsDetailRow("Speed", "%.1f km/h".format(snapshot.directSpeedKmh))
                SettingsDetailRow(
                    "Gear",
                    snapshot.gear
                )
                SettingsDetailRow(
                    "Pedals",
                    "A=${directIntOrNA(snapshot.speedAccelerateDeepness)}, " +
                        "B=${directIntOrNA(snapshot.speedBrakeDeepness)}, " +
                        "brake=${directIntOrNA(snapshot.gearboxBrakePedalState)}"
                )
                SettingsDetailRow(
                    "Signals",
                    "flash=${directIntOrNA(snapshot.turnSignalFlashState)}, " +
                        "L=${directBoolOrNA(snapshot.turnSignalLeft)}, " +
                        "R=${directBoolOrNA(snapshot.turnSignalRight)}"
                )
                SettingsDetailRow(
                    "Trip",
                    "avg=${snapshot.instrumentAverageSpeed?.let { "%.1f km/h".format(it) } ?: "n/a"}, " +
                        "journey=${snapshot.instrumentCurrentJourneyDriveMileage?.let { "%.1f km".format(it) } ?: "n/a"}, " +
                        "time=${snapshot.instrumentCurrentJourneyDriveTime?.let { "%.1f".format(it) } ?: "n/a"}"
                )
                HorizontalDivider()
                Text(
                    "Debug",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SettingsDetailRow(
                    "Bodywork",
                    "auto=${directIntOrNA(snapshot.bodyworkAutoSystemState)}, power=${directIntOrNA(snapshot.bodyworkPowerLevel)}"
                )
                SettingsDetailRow(
                    "Battery dbg",
                    "capacity=${directIntOrNA(snapshot.bodyworkBatteryCapacity)}, " +
                        "hev=${snapshot.bodyworkBatteryPowerHEV ?: "n/a"}, " +
                        "value=${directIntOrNA(snapshot.bodyworkBatteryPowerValue)}, " +
                        "voltageLevel=${directIntOrNA(snapshot.bodyworkBatteryVoltageLevel)}"
                )
                SettingsDetailRow("Sensor", "temp=${snapshot.sensorTemperatureValue ?: "n/a"}")

                if (snapshot.probeValues.isNotEmpty()) {
                    HorizontalDivider()
                    SettingsGroupLabel("Probe values")
                    snapshot.probeValues.toSortedMap().forEach { (key, value) ->
                        SettingsDetailRow(
                            formatProbeLabel(key),
                            directProbeValue(value, key)
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = onRefresh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Refresh direct snapshot", fontSize = 16.sp)
            }
        }
    }
}

private fun directIntOrNA(value: Int?): String {
    return when (value) {
        null -> "n/a"
        65535, -2147482645 -> "n/a"
        else -> {
            if (value <= -10000) "n/a" else value.toString()
        }
    }
}

private fun directBoolOrNA(value: Boolean?): String {
    return when (value) {
        null -> "n/a"
        true -> "on"
        false -> "off"
    }
}

private fun directProbeValue(value: Double, key: String): String {
    val suffix = when {
        key.endsWith("_kw") -> " kW"
        key.endsWith("_pct") -> "%"
        key.endsWith("_c") -> "°C"
        else -> ""
    }
    return String.format("%.2f%s", value, suffix)
}

private fun formatProbeLabel(key: String): String {
    return key.split('_')
        .filter { it.isNotBlank() }
        .joinToString(" ") { token ->
            token.replaceFirstChar { ch -> ch.uppercase() }
        }
}

@Composable
internal fun VehicleSnapshotCard(
    telemetry: VehicleTelemetry?,
    snapshot: VehicleTelemetrySnapshot?
) {
    val chargingLabel = if (snapshot == null) stringResource(R.string.waiting_vehicle_data_msg) else {
        val hrs = snapshot.remainHours
        val mins = snapshot.remainMinutes
        if (hrs > 0 || mins > 0) "${hrs}h ${mins}m"
        else "n/a"
    }
    val chargingPowerKw = telemetry?.chargingPower ?: 0.0

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Filled.DirectionsCar, null, tint = BydElectricAzure, modifier = Modifier.size(22.dp))
                Text(stringResource(R.string.vehicle_snapshot_label), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            if (snapshot == null) {
                Text(
                    "Vehicle data is not connected yet. Start the app or wake the car to populate this card.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                SettingsDetailRow("Gear", snapshot.gear)
                SettingsDetailRow("Charging", "%.1f kW".format(chargingPowerKw))
                SettingsDetailRow("Charging energy", "%.3f kWh".format(snapshot.chargingCapacity))
                SettingsDetailRow("Charge active", if (snapshot.isChargingActive) "yes" else "no")
                SettingsDetailRow(
                    "Charge cap",
                    "state ${snapshot.chargingCapState}, value ${snapshot.chargingCapValue}"
                )
                SettingsDetailRow("Charge time", chargingLabel)
                SettingsDetailRow(
                    "Tyres",
                    "LF ${directTyrePsiOrNA(snapshot.tyrePressureLFPsi, snapshot.tyrePressureLFState)} | " +
                        "RF ${directTyrePsiOrNA(snapshot.tyrePressureRFPsi, snapshot.tyrePressureRFState)} | " +
                        "LR ${directTyrePsiOrNA(snapshot.tyrePressureLRPsi, snapshot.tyrePressureLRState)} | " +
                        "RR ${directTyrePsiOrNA(snapshot.tyrePressureRRPsi, snapshot.tyrePressureRRState)}"
                )
                SettingsDetailRow(
                    "States",
                    "charger ${snapshot.chargerState}/${snapshot.chargerWorkState}, " +
                        "gun ${snapshot.chargingGunState}, type ${snapshot.chargingType}, mode ${snapshot.chargingMode}, " +
                        "cap ${snapshot.chargingCapacity}"
                )
            }
        }
    }
}

@Composable
internal fun TelemetryComparisonCard(
    telemetry: VehicleTelemetry?,
    snapshot: VehicleTelemetrySnapshot?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Filled.Timeline, null, tint = BydElectricAzure, modifier = Modifier.size(22.dp))
                Text(stringResource(R.string.telemetry_compare_label), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Text(
                stringResource(R.string.auto_refreshes_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (telemetry == null && snapshot == null) {
                Text(
                    stringResource(R.string.waiting_telemetry_msg),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                SettingsGroupLabel("Minor-drive")
                SettingsDetailRow(
                    "Gear",
                    liveVsCar(
                        telemetry?.gear,
                        snapshot?.gear
                    )
                )
                SettingsDetailRow(
                    "Speed",
                    liveVsCar(
                        telemetry?.let { "%.1f km/h".format(it.speed) },
                        snapshot?.let { "%.1f km/h".format(it.directSpeedKmh) }
                    )
                )
                SettingsDetailRow(
                    "Pedals",
                    "Live: n/a   |   Car: A=${snapshot?.speedAccelerateDeepness?.toString() ?: "n/a"}, " +
                        "B=${snapshot?.speedBrakeDeepness?.toString() ?: "n/a"}, " +
                        "brake=${snapshot?.gearboxBrakePedalState?.toString() ?: "n/a"}"
                )
                SettingsDetailRow(
                    "Trip",
                    "Live: n/a   |   Car: avg=${snapshot?.instrumentAverageSpeed?.let { "%.1f".format(it) } ?: "n/a"}, " +
                        "journey=${snapshot?.instrumentCurrentJourneyDriveMileage?.let { "%.1f".format(it) } ?: "n/a"}, " +
                        "time=${snapshot?.instrumentCurrentJourneyDriveTime?.let { "%.1f".format(it) } ?: "n/a"}"
                )
                HorizontalDivider()
                SettingsGroupLabel(stringResource(R.string.remote_only_label))
                SettingsDetailRow(
                    "Battery remain (kWh)",
                    "Live: n/a   |   Car: ${snapshot?.powerBatteryRemainPowerEV?.let { "%.1f kWh".format(it) } ?: "n/a"}"
                )
                SettingsDetailRow(
                    "Outside temp",
                    "Live: n/a   |   Car: ${snapshot?.instrumentOutCarTemperature?.let { "${it}°C" } ?: "n/a"}"
                )
                SettingsDetailRow(
                    "VIN",
                    "Live: n/a   |   Car: ${snapshot?.bodyworkAutoVin ?: "n/a"}"
                )
                SettingsDetailRow(
                    "Seatbelt",
                    "Live: n/a   |   Car: D=${snapshot?.instrumentSafetyBeltDriverStatus?.toString() ?: "n/a"}, " +
                        "P=${snapshot?.instrumentSafetyBeltPassengerStatus?.toString() ?: "n/a"}"
                )
                SettingsDetailRow(
                    "Signals",
                    "Live: n/a   |   Car: flash=${snapshot?.turnSignalFlashState?.toString() ?: "n/a"}, " +
                        "L=${directBoolOrNA(snapshot?.turnSignalLeft)}, " +
                        "R=${directBoolOrNA(snapshot?.turnSignalRight)}"
                )
                SettingsDetailRow(
                    "Tyres",
                    liveVsCar(
                        telemetry?.let {
                            "LF %.1f | RF %.1f | LR %.1f | RR %.1f psi".format(
                                it.tyrePressureLF,
                                it.tyrePressureRF,
                                it.tyrePressureLR,
                                it.tyrePressureRR
                            )
                        },
                        snapshot?.let {
                            "LF ${directTyrePsiOrNA(it.tyrePressureLFPsi, it.tyrePressureLFState)} | " +
                                "RF ${directTyrePsiOrNA(it.tyrePressureRFPsi, it.tyrePressureRFState)} | " +
                                "LR ${directTyrePsiOrNA(it.tyrePressureLRPsi, it.tyrePressureLRState)} | " +
                                "RR ${directTyrePsiOrNA(it.tyrePressureRRPsi, it.tyrePressureRRState)}"
                        }
                    )
                )
            }
        }
    }
}

private fun directTyrePsiOrNA(psi: Double, state: Int?): String {
    return if (state != null && state != 0) "n/a" else if (psi > 0.0) "%.1f psi".format(psi) else "n/a"
}

private fun liveVsCar(live: String?, car: String?): String {
    val left = live?.takeIf { it.isNotBlank() } ?: "n/a"
    val right = car?.takeIf { it.isNotBlank() } ?: "n/a"
    return "Live: $left   |   Car: $right"
}

@Composable
internal fun CoreTelemetryCard(telemetry: VehicleTelemetry?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Filled.Dashboard, null, tint = BydElectricAzure, modifier = Modifier.size(22.dp))
                Text(stringResource(R.string.core_telemetry_label), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            if (telemetry == null) {
                Text(
                    stringResource(R.string.waiting_live_msg),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                SettingsGroupLabel(stringResource(R.string.core_section_label))
                SettingsDetailRow("SoC", "%.1f%%".format(telemetry.soc))
                SettingsDetailRow("SoC panel", "${telemetry.socPanel}%")
                SettingsDetailRow("Car on", telemetry.isCarOn.toString())
                SettingsDetailRow("Locked", telemetry.carLocked.toString())
                SettingsDetailRow("Door open", telemetry.anyDoorOpened.toString())
                SettingsDetailRow("Gear", telemetry.gear)
                SettingsDetailRow("Speed", "%.1f km/h".format(telemetry.speed))
                SettingsDetailRow("Odometer", "%.1f km".format(telemetry.odometer))
                SettingsDetailRow("Engine power", "${telemetry.enginePower} kW")
                SettingsDetailRow("Total discharge", "%.1f".format(telemetry.totalDischarge))
                SettingsDetailRow("Charging", "%.1f kW".format(telemetry.chargingPower))
                SettingsDetailRow("Electric range", "${telemetry.electricDrivingRangeKm} km")
                SettingsDetailRow("Fuel range", "${telemetry.fuelDrivingRangeKm} km")
                SettingsDetailRow("Fuel level", "${telemetry.fuelPercentage}%")
                SettingsDetailRow("Engine front", "${telemetry.engineSpeedFront} rpm")
                SettingsDetailRow("Engine rear", "${telemetry.engineSpeedRear} rpm")
                SettingsDetailRow("12V", "%.1f V".format(telemetry.battery12vVoltage))
                SettingsDetailRow("Battery", "%.1f V".format(telemetry.batteryTotalVoltage.toDouble()))
                SettingsDetailRow(
                    if (telemetry.sohEstimated) "Estimated SoH" else "SOH",
                    telemetry.soh.takeIf { it in 1..100 }?.let { "$it%" } ?: "—"
                )
                SettingsDetailRow(
                    "Battery temp",
                    "max=${telemetry.batteryCellTempMax}°C, min=${telemetry.batteryCellTempMin}°C, avg=${"%.1f".format(telemetry.batteryTempAvg)}°C"
                )
                SettingsDetailRow(
                    "Battery cells",
                    "Vmax ${"%.3f".format(telemetry.batteryCellVoltageMax)} / Vmin ${"%.3f".format(telemetry.batteryCellVoltageMin)}"
                )
                SettingsGroupLabel(stringResource(R.string.stat_battery))
                SettingsDetailRow("Cell V max", "%.3f V".format(telemetry.batteryCellVoltageMax))
                SettingsDetailRow("Cell V min", "%.3f V".format(telemetry.batteryCellVoltageMin))
                SettingsDetailRow("Current date", if (telemetry.currentDatetime.isBlank()) "n/a" else telemetry.currentDatetime)
                SettingsDetailRow(
                    "Date / location",
                    if (telemetry.currentDatetime.isNotBlank()) {
                        "${telemetry.currentDatetime}, %.5f, %.5f @ %.0f m".format(
                            telemetry.locationLatitude,
                            telemetry.locationLongitude,
                            telemetry.locationAltitude
                        )
                    } else {
                        "%.5f, %.5f @ %.0f m".format(
                            telemetry.locationLatitude,
                            telemetry.locationLongitude,
                            telemetry.locationAltitude
                        )
                    }
                )
                SettingsDetailRow("Wi-Fi", if (telemetry.wifiSsid.isBlank()) "n/a" else telemetry.wifiSsid)

                SettingsGroupLabel("Vehicle overlay")
                SettingsDetailRow(
                    "Battery remain (kWh)",
                    telemetry.batteryRemainPowerEV?.let { "%.1f kWh".format(it) } ?: "n/a"
                )
                SettingsDetailRow(
                    "Avg speed",
                    telemetry.averageSpeed?.let { "%.1f km/h".format(it) } ?: "n/a"
                )
                SettingsDetailRow(
                    "Outside temp",
                    telemetry.instrumentOutCarTemperature?.let { "${it}°C" } ?: "n/a"
                )
                SettingsDetailRow(
                    "Seatbelt",
                    "D=${telemetry.instrumentSafetyBeltDriverStatus?.toString() ?: "n/a"}, " +
                        "P=${telemetry.instrumentSafetyBeltPassengerStatus?.toString() ?: "n/a"}"
                )
                SettingsDetailRow(
                    "Signals",
                    "flash=${telemetry.turnSignalFlashState?.toString() ?: "n/a"}, " +
                        "L=${telemetry.turnSignalLeft?.toString() ?: "n/a"}, " +
                        "R=${telemetry.turnSignalRight?.toString() ?: "n/a"}"
                )
            }
        }
    }
}

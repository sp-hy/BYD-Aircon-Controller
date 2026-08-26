package com.byd.tripstats.data.model

import kotlinx.serialization.Serializable

@Serializable
data class BatteryVoltageHistoryPoint(
    val timestamp: Long,
    val battery12vVoltage: Double,
    val batteryTotalVoltage: Int,
    val isChargingSample: Boolean,
    val soc: Double = 0.0,
    val socPanel: Int = 0
)

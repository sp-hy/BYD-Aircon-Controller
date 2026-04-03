package com.sphy.airconcontroller.ble

import android.content.Context

enum class BleConnectionState {
    IDLE,
    SCANNING,
    CONNECTING,
    CONNECTED,
    DISCONNECTED
}

private fun BleConnectionState.resourceName(): String =
    when (this) {
        BleConnectionState.IDLE -> "ble_status_idle"
        BleConnectionState.SCANNING -> "ble_status_scanning"
        BleConnectionState.CONNECTING -> "ble_status_connecting"
        BleConnectionState.CONNECTED -> "ble_status_connected"
        BleConnectionState.DISCONNECTED -> "ble_status_disconnected"
    }

fun BleConnectionState.toDisplayString(context: Context): String {
    val resId = context.resources.getIdentifier(resourceName(), "string", context.packageName)
    require(resId != 0) { "Missing string resource: ${resourceName()}" }
    return context.getString(resId)
}

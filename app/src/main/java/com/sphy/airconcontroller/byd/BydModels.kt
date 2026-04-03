package com.sphy.airconcontroller.byd

data class BydConfig(
    val username: String,
    val password: String,
    val controlPin: String,
    val countryCode: String,
    val baseUrl: String
)

data class BydSession(
    val userId: String,
    val signToken: String,
    val encryToken: String
)

data class VehicleSummary(
    val vin: String,
    val modelName: String?
)

enum class ClimateCommandType(val commandType: String) {
    START("OPENAIR"),
    STOP("CLOSEAIR")
}

enum class SeatPosition(val chairType: String) {
    DRIVER("1"),
    PASSENGER("2")
}

enum class SeatMode {
    HEAT,
    COOL
}

enum class SeatLevel(val commandValue: Int) {
    OFF(3),   // OFF → 3
    LOW(2),   // LOW → 2
    HIGH(1);  // HIGH → 1

    fun next(): SeatLevel = when (this) {
        OFF -> LOW
        LOW -> HIGH
        HIGH -> OFF
    }
}

data class CommandResult(
    val success: Boolean,
    val controlState: Int,
    val requestSerial: String?
)

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

data class CommandResult(
    val success: Boolean,
    val controlState: Int,
    val requestSerial: String?
)

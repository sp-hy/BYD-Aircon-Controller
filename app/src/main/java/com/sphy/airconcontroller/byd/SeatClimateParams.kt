package com.sphy.airconcontroller.byd

import org.json.JSONObject

/**
 * Maps [getStatusNow] seat fields to VENTILATIONHEATING command scale, matching pyBYD
 * [SeatHeatVentState.to_command_level] / [SeatClimateParams.from_current_state].
 *
 * Command scale: HIGH=1, LOW=2, OFF=3; 0 = not applicable / no change.
 */
internal object SeatClimateParams {

    private val SEAT_HINT_KEYS = arrayOf(
        "mainSeatHeatState", "mainSeatVentilationState",
        "copilotSeatHeatState", "copilotSeatVentilationState",
        "lrSeatHeatState", "lrSeatVentilationState",
        "lrThirdHeatState", "lrThirdVentilationState",
        "rrSeatHeatState", "rrSeatVentilationState",
        "rrThirdHeatState", "rrThirdVentilationState",
        "steeringWheelHeatState", "stearingWheelHeatState",
        "main_seat_heat_state", "main_seat_ventilation_state",
        "copilot_seat_heat_state", "copilot_seat_ventilation_state"
    )

    /** True if the slice contains at least one seat-related field from the API. */
    fun sliceContainsSeatHints(slice: JSONObject): Boolean =
        SEAT_HINT_KEYS.any { slice.has(it) }

    private fun JSONObject.optIntIfPresent(key: String): Int? {
        if (!has(key)) return null
        return optInt(key)
    }

    private fun JSONObject.optIntAny(vararg keys: String): Int? {
        for (k in keys) {
            val v = optIntIfPresent(k)
            if (v != null) return v
        }
        return null
    }

    private fun seatStatusToCommand(status: Int): Int = when (status) {
        -1 -> 0
        0 -> 0
        1 -> 3
        2 -> 2
        3 -> 1
        else -> 0
    }

    private fun steeringWheelStatusToCommand(status: Int): Int = when (status) {
        -1 -> 1
        else -> 3
    }

    /**
     * Extracts the inner `statusNow` object if present (as pyBYD [HvacStatus] does).
     */
    fun effectiveStatusSlice(root: JSONObject): JSONObject {
        val nested = root.optJSONObject("statusNow")
        return nested ?: root
    }

    /**
     * Builds `controlParamsMap` fields from live HVAC status, using camelCase keys like pyBYD
     * `model_dump(by_alias=True)` for [SeatClimateParams].
     */
    fun fromHvacStatusJson(status: JSONObject): JSONObject {
        fun seatCmd(vararg keys: String): Int {
            val raw = status.optIntAny(*keys) ?: return 0
            return seatStatusToCommand(raw)
        }

        val swRaw = status.optIntAny("steeringWheelHeatState", "stearingWheelHeatState")
        val steering = if (swRaw != null) steeringWheelStatusToCommand(swRaw) else 3

        return JSONObject().apply {
            put("mainHeat", seatCmd("mainSeatHeatState", "main_seat_heat_state"))
            put("mainVentilation", seatCmd("mainSeatVentilationState", "main_seat_ventilation_state"))
            put("copilotHeat", seatCmd("copilotSeatHeatState", "copilot_seat_heat_state"))
            put("copilotVentilation", seatCmd("copilotSeatVentilationState", "copilot_seat_ventilation_state"))
            put("lrSeatHeatState", seatCmd("lrSeatHeatState", "lr_seat_heat_state"))
            put("lrSeatVentilationState", seatCmd("lrSeatVentilationState", "lr_seat_ventilation_state"))
            put("lrThirdHeatState", seatCmd("lrThirdHeatState", "lr_third_heat_state"))
            put("lrThirdVentilationState", seatCmd("lrThirdVentilationState", "lr_third_ventilation_state"))
            put("rrSeatHeatState", seatCmd("rrSeatHeatState", "rr_seat_heat_state"))
            put("rrSeatVentilationState", seatCmd("rrSeatVentilationState", "rr_seat_ventilation_state"))
            put("rrThirdHeatState", seatCmd("rrThirdHeatState", "rr_third_heat_state"))
            put("rrThirdVentilationState", seatCmd("rrThirdVentilationState", "rr_third_ventilation_state"))
            put("steeringWheelHeatState", steering)
        }
    }

    /**
     * pyBYD [SeatClimateParams] model defaults when status cannot be read.
     */
    fun fallbackDefaults(): JSONObject {
        return JSONObject().apply {
            put("mainHeat", 3)
            put("mainVentilation", 0)
            put("copilotHeat", 3)
            put("copilotVentilation", 0)
            put("lrSeatHeatState", 0)
            put("lrSeatVentilationState", 0)
            put("lrThirdHeatState", 0)
            put("lrThirdVentilationState", 0)
            put("rrSeatHeatState", 0)
            put("rrSeatVentilationState", 0)
            put("rrThirdHeatState", 0)
            put("rrThirdVentilationState", 0)
            put("steeringWheelHeatState", 3)
        }
    }
}

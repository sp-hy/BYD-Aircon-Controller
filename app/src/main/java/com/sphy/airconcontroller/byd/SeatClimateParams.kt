package com.sphy.airconcontroller.byd

import org.json.JSONObject

/**
 * Maps [getStatusNow] seat fields to VENTILATIONHEATING command scale, matching pyBYD
 * [SeatHeatVentState.to_command_level] / [SeatClimateParams.from_current_state].
 *
 * Command scale: HIGH=1, LOW=2, OFF=3; 0 = not applicable / no change.
 */
internal object SeatClimateParams {

    /** Parse JSON number / numeric string the way pyBYD enums see after cleaning. */
    fun jsonNumberToInt(raw: Any?): Int? {
        return when (raw) {
            null -> null
            is Int -> raw
            is Long -> raw.toInt()
            is Double -> raw.toInt()
            is Float -> raw.toInt()
            is String -> raw.trim().toIntOrNull()
            else -> null
        }
    }

    private fun JSONObject.optIntFlexible(key: String): Int? {
        if (!has(key)) return null
        return jsonNumberToInt(get(key))
    }

    private fun JSONObject.optIntAny(vararg keys: String): Int? {
        for (k in keys) {
            val v = optIntFlexible(k)
            if (v != null) return v
        }
        return null
    }

    /**
     * HVAC `status` (1=on) for [isClimateOn]: prefer root, else [statusNow].
     */
    fun overallClimateStatus(root: JSONObject): Int {
        if (root.has("status")) return root.optInt("status", -1)
        val sn = root.optJSONObject("statusNow")
        if (sn != null && sn.has("status")) return sn.optInt("status", -1)
        return -1
    }

    /**
     * Best single-shot snapshot for [fromHvacStatusJson] without MQTT realtime.
     *
     * pyBYD [SeatClimateParams.from_current_state] uses **hvac first, then realtime** to fill each
     * seat field. The app only has `/control/getStatusNow`. BYD often puts some fields on the **root**
     * and updates in **statusNow** — using only `statusNow` (HvacStatus unwrap) drops root-only
     * seat keys; a **merge** approximates hvac ∪ realtime overlap. Keys in `statusNow` win on clash.
     */
    fun mergedHvacStatusForSeatBaseline(root: JSONObject): JSONObject {
        val out = JSONObject()
        val rootKeys = root.keys()
        while (rootKeys.hasNext()) {
            val k = rootKeys.next()
            if (k == "statusNow") continue
            out.put(k, root.get(k))
        }
        val sn = root.optJSONObject("statusNow")
        if (sn != null) {
            val snKeys = sn.keys()
            while (snKeys.hasNext()) {
                val k = snKeys.next()
                out.put(k, sn.get(k))
            }
        }
        if (out.has("stearingWheelHeatState") && !out.has("steeringWheelHeatState")) {
            out.put("steeringWheelHeatState", out.get("stearingWheelHeatState"))
        }
        return out
    }

    /**
     * pyBYD [SeatClimateParams.from_current_state]: each seat field from `hvac` if present, else
     * `realtime`. Implemented by overlaying keys from [realtime] only where [hvac] lacks the key.
     */
    fun mergeHvacWithRealtimeFallback(hvac: JSONObject, realtime: JSONObject?): JSONObject {
        if (realtime == null) return hvac
        val out = JSONObject(hvac.toString())
        val rtKeys = realtime.keys()
        while (rtKeys.hasNext()) {
            val k = rtKeys.next()
            if (!out.has(k)) {
                out.put(k, realtime.get(k))
            }
        }
        if (out.has("stearingWheelHeatState") && !out.has("steeringWheelHeatState")) {
            out.put("steeringWheelHeatState", out.get("stearingWheelHeatState"))
        }
        return out
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
     * Remote session often ends when the car sees **no** active remote climate need. Sending explicit
     * OFF (`3`) on **both** front seats (driver `main*` from HTTP + passenger `copilot*` vent off) can
     * look like “everything off”, so the vehicle drops remote power. For the seat we are **not**
     * changing, send `0` (no change) instead of mirroring getStatusNow OFF (`3`).
     */
    fun maskNonTargetFrontSeatAsNoChange(params: JSONObject, position: SeatPosition) {
        when (position) {
            SeatPosition.DRIVER -> {
                params.put("copilotHeat", 0)
                params.put("copilotVentilation", 0)
            }
            SeatPosition.PASSENGER -> {
                params.put("mainHeat", 0)
                params.put("mainVentilation", 0)
            }
        }
    }

    /** pyBYD serialises [controlParamsMap] with [json.dumps(..., sort_keys=True)]. */
    fun sortedControlParamsMapString(params: JSONObject): String {
        val keys = params.keys().asSequence().sorted().toList()
        val sorted = JSONObject()
        for (k in keys) {
            sorted.put(k, params.get(k))
        }
        return sorted.toString()
    }

    /**
     * Builds `controlParamsMap` from merged status (camelCase keys like pyBYD `model_dump(by_alias=True)`).
     * Third-row fields are filled from JSON when present ([VehicleRealtimeData] in pyBYD); else 0.
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

    /** When [getStatusNow] is unavailable or empty. Matches pyBYD missing enum → 0 for seat fields. */
    fun fallbackDefaults(): JSONObject {
        return JSONObject().apply {
            put("mainHeat", 0)
            put("mainVentilation", 0)
            put("copilotHeat", 0)
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

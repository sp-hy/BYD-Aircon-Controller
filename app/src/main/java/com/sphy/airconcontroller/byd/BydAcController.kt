package com.sphy.airconcontroller.byd

import android.content.Context
import android.util.Log
import java.lang.reflect.InvocationTargetException

/**
 * Reflective client for `android.hardware.bydauto.ac.BYDAutoAcDevice`.
 *
 * Method names follow the public BYD Auto SDK / on-car dumps (note the `Temprature` typo).
 * Several SET overloads are tried because DiLink 3 vs 5 signatures drift.
 */
class BydAcController(context: Context) {
    private val appContext = context.applicationContext
    private val permContext = BydPermissionContext(appContext)

    @Volatile
    private var device: Any? = null

    @Volatile
    var lastBindError: String? = null
        private set

    data class CommandResult(
        val success: Boolean,
        val method: String,
        val raw: Int?,
        val detail: String
    )

    data class AcSnapshot(
        val sdkInjected: Boolean,
        val bound: Boolean,
        val bindError: String?,
        val powerOn: Boolean?,
        val driverTempC: Int?,
        val passengerTempC: Int?,
        val outsideTempC: Int?,
        val fanLevel: Int?,
        val controlMode: Int?,
        val cycleMode: Int?,
        val compressorMode: Int?,
        val maxCool: Boolean?,
        val ventilation: Boolean?
    ) {
        fun toDisplayString(): String {
            if (!sdkInjected) {
                return "OEM SDK not loaded. Grant ADB + hidden-API access, then reopen the app.\n" +
                    (bindError?.let { "Detail: $it" } ?: "")
            }
            if (!bound) {
                return "AC device not bound.\n" + (bindError ?: "getInstance returned null")
            }
            return buildString {
                appendLine("AC: ${fmtOnOff(powerOn)}")
                appendLine("Driver set: ${fmtTemp(driverTempC)}")
                appendLine("Passenger set: ${fmtTemp(passengerTempC)}")
                appendLine("Outside: ${fmtTemp(outsideTempC)}")
                appendLine("Fan: ${fanLevel?.toString() ?: "—"}")
                appendLine("Mode: ${fmtControl(controlMode)}")
                appendLine("Air: ${fmtCycle(cycleMode)}")
                appendLine("Compressor: ${compressorMode?.toString() ?: "—"}")
                appendLine("Max cool: ${fmtOnOff(maxCool)}")
                appendLine("Ventilation: ${fmtOnOff(ventilation)}")
            }.trimEnd()
        }
    }

    fun bind(): Boolean {
        lastBindError = null
        if (device != null) return true

        val injected = Dilink5SdkInjector.ensure(appContext)
        if (!injected && !Dilink5SdkInjector.isLoadable(appContext)) {
            lastBindError = "Could not inject com.byd.data.collect (hidden-API exemption missing, or OEM app not installed)"
            return false
        }

        return try {
            val cls = Class.forName(AC_CLASS)
            val method = cls.getMethod("getInstance", Context::class.java)
            val inst = invokeGetInstance(method, permContext) ?: invokeGetInstance(method, appContext)
            if (inst == null) {
                if (lastBindError == null) lastBindError = "BYDAutoAcDevice.getInstance returned null"
                false
            } else {
                device = inst
                lastBindError = null
                Log.i(TAG, "bound BYDAutoAcDevice via ${inst.javaClass.name}")
                true
            }
        } catch (t: Throwable) {
            val c = (t as? InvocationTargetException)?.cause ?: t
            lastBindError = "${c.javaClass.simpleName}: ${c.message}"
            Log.w(TAG, "bind failed: $lastBindError")
            false
        }
    }

    private fun invokeGetInstance(method: java.lang.reflect.Method, ctx: Context): Any? {
        return try {
            method.invoke(null, ctx)
        } catch (t: Throwable) {
            val c = (t as? InvocationTargetException)?.cause ?: t
            lastBindError = "${c.javaClass.simpleName}: ${c.message}"
            null
        }
    }

    fun isBound(): Boolean = device != null || bind()

    fun snapshot(): AcSnapshot {
        val injected = Dilink5SdkInjector.isLoadable(appContext) || Dilink5SdkInjector.ensure(appContext)
        val ok = isBound()
        val dev = device
        return AcSnapshot(
            sdkInjected = injected,
            bound = ok && dev != null,
            bindError = lastBindError,
            powerOn = intToBool(getInt("getAcStartState")),
            driverTempC = plausibleTemp(getIntArg("getTemprature", ZONE_DRIVER) ?: getIntArg("getTemperature", ZONE_DRIVER)),
            passengerTempC = plausibleTemp(getIntArg("getTemprature", ZONE_PASSENGER) ?: getIntArg("getTemperature", ZONE_PASSENGER)),
            outsideTempC = plausibleTemp(getIntArg("getTemprature", ZONE_OUTSIDE) ?: getIntArg("getTemperature", ZONE_OUTSIDE)),
            fanLevel = getInt("getAcWindLevel"),
            controlMode = getInt("getAcControlMode"),
            cycleMode = getInt("getAcCycleMode"),
            compressorMode = getInt("getAcCompressorMode"),
            maxCool = intToBool(getInt("getAcMaxCoolingState")),
            ventilation = intToBool(getInt("getAcVentilationState")),
        )
    }

    fun start(): CommandResult {
        if (!ensureDevice()) return notBound()
        return firstSuccess(
            call("start", 0),
            call("start", 1),
            call("setAcStartState", AC_POWER_ON),
            call("setAcStartState", AC_POWER_ON, 0),
        )
    }

    fun stop(): CommandResult {
        if (!ensureDevice()) return notBound()
        return firstSuccess(
            call("stop", 0),
            call("stop", 1),
            call("setAcStartState", AC_POWER_OFF),
            call("setAcStartState", AC_POWER_OFF, 0),
        )
    }

    fun setDriverTemp(celsius: Int): CommandResult {
        if (!ensureDevice()) return notBound()
        val temp = celsius.coerceIn(TEMP_MIN, TEMP_MAX)
        return firstSuccess(
            call("setAcTemperature", ZONE_DRIVER, temp, SOURCE_VOICE, 1),
            call("setAcTemprature", ZONE_DRIVER, temp, SOURCE_VOICE, 1),
            call("setAcTemperature", ZONE_DRIVER, temp, SOURCE_UI, 0),
            call("setTemprature", ZONE_DRIVER, temp),
            call("setTemperature", ZONE_DRIVER, temp),
        )
    }

    fun nudgeDriverTemp(delta: Int): CommandResult {
        val current = snapshot().driverTempC ?: 22
        return setDriverTemp(current + delta)
    }

    fun setFanLevel(level: Int): CommandResult {
        if (!ensureDevice()) return notBound()
        val lv = level.coerceIn(FAN_MIN, FAN_MAX)
        val named = firstSuccess(
            call("setAcWindLevel", lv, SOURCE_VOICE),
            call("setAcWindLevel", lv, SOURCE_UI),
            call("setAcWindLevel", lv),
        )
        if (named.success) return named
        val fallback = call("set", DEVICE_TYPE_AC, FEATURE_WIND_LEVEL, lv)
        return if (fallback.success) fallback else named
    }

    fun nudgeFan(delta: Int): CommandResult {
        val current = snapshot().fanLevel ?: 3
        return setFanLevel(current + delta)
    }

    fun setAuto(enabled: Boolean): CommandResult {
        if (!ensureDevice()) return notBound()
        val mode = if (enabled) CONTROL_AUTO else CONTROL_MANUAL
        return firstSuccess(
            call("setAcControlMode", mode, SOURCE_VOICE),
            call("setAcControlMode", mode, SOURCE_UI),
            call("setAcControlMode", mode),
        )
    }

    fun toggleRecirc(): CommandResult {
        if (!ensureDevice()) return notBound()
        val current = snapshot().cycleMode ?: 0
        val next = if (current == CYCLE_RECIRC) CYCLE_FRESH else CYCLE_RECIRC
        return firstSuccess(
            call("setAcCycleMode", next, SOURCE_UI),
            call("setAcCycleMode", next, SOURCE_VOICE),
            call("setAcCycleMode", next),
        )
    }

    fun setMaxCool(on: Boolean): CommandResult {
        if (!ensureDevice()) return notBound()
        val state = if (on) 1 else 0
        return firstSuccess(
            call("setAcMaxCoolingState", state),
            call("setAcMaxCoolingState", state, SOURCE_VOICE),
        )
    }

    fun dumpMethods(): String {
        if (!ensureDevice()) return lastBindError ?: "AC device not bound"
        val methods = device!!.javaClass.methods
            .filter { it.declaringClass.name.contains("bydauto") || it.name.startsWith("get") || it.name.startsWith("set") || it.name == "start" || it.name == "stop" }
            .sortedBy { it.name }
        return methods.joinToString("\n") { m ->
            "${m.name}(${m.parameterTypes.joinToString(",") { it.simpleName }}):${m.returnType.simpleName}"
        }.ifBlank { "No bydauto methods visible on ${device!!.javaClass.name}" }
    }

    private fun ensureDevice(): Boolean = isBound()

    private fun notBound() = CommandResult(
        success = false,
        method = "(unbound)",
        raw = null,
        detail = lastBindError ?: "AC device not bound"
    )

    private fun firstSuccess(vararg results: CommandResult): CommandResult {
        results.firstOrNull { it.success }?.let { return it }
        return results.lastOrNull { it.detail != "no such method" } ?: results.first()
    }

    private fun call(name: String, vararg args: Any): CommandResult {
        val dev = device ?: return notBound()
        val types = args.map { argType(it) }.toTypedArray()
        return try {
            val method = dev.javaClass.getMethod(name, *types)
            val boxed = method.invoke(dev, *args)
            val raw = (boxed as? Number)?.toInt()
            val ok = raw == null || isSuccessCode(raw)
            CommandResult(
                success = ok,
                method = formatCall(name, args),
                raw = raw,
                detail = describeCode(raw)
            )
        } catch (e: NoSuchMethodException) {
            CommandResult(false, formatCall(name, args), null, "no such method")
        } catch (t: Throwable) {
            val c = (t as? InvocationTargetException)?.cause ?: t
            CommandResult(false, formatCall(name, args), null, "${c.javaClass.simpleName}: ${c.message}")
        }
    }

    private fun getInt(name: String): Int? {
        val dev = device ?: return null
        return runCatching {
            (dev.javaClass.getMethod(name).invoke(dev) as? Number)?.toInt()
        }.getOrNull()?.takeUnless { it in SENTINELS }
    }

    private fun getIntArg(name: String, arg: Int): Int? {
        val dev = device ?: return null
        return runCatching {
            (dev.javaClass.getMethod(name, Int::class.javaPrimitiveType).invoke(dev, arg) as? Number)?.toInt()
        }.getOrNull()?.takeUnless { it in SENTINELS }
    }

    companion object {
        private const val TAG = "BydAcController"
        private const val AC_CLASS = "android.hardware.bydauto.ac.BYDAutoAcDevice"
        private const val ZONE_DRIVER = 1
        private const val ZONE_PASSENGER = 2
        private const val ZONE_OUTSIDE = 4
        private const val AC_POWER_OFF = 0
        private const val AC_POWER_ON = 1
        private const val SOURCE_UI = 0
        private const val SOURCE_VOICE = 1
        private const val CONTROL_AUTO = 0
        private const val CONTROL_MANUAL = 1
        private const val CYCLE_RECIRC = 0
        private const val CYCLE_FRESH = 1
        private const val DEVICE_TYPE_AC = 1000
        private const val FEATURE_WIND_LEVEL = 0x1DE0000C
        const val TEMP_MIN = 17
        const val TEMP_MAX = 32
        private const val FAN_MIN = 0
        private const val FAN_MAX = 7

        private val SENTINELS = setOf(
            -1,
            -2147482645,
            -2147482646,
            -2147482647,
            -2147482648,
            65535,
        )

        private fun argType(arg: Any): Class<*> = when (arg) {
            is Int -> Int::class.javaPrimitiveType!!
            is String -> String::class.java
            else -> arg.javaClass
        }

        private fun formatCall(name: String, args: Array<out Any>): String =
            "$name(${args.joinToString(", ")})"

        private fun isSuccessCode(raw: Int): Boolean = raw >= 0

        private fun describeCode(raw: Int?): String = when (raw) {
            null -> "invoked (void/unknown)"
            0 -> "success"
            -2147482648 -> "failed"
            -2147482647 -> "busy"
            -2147482646 -> "timeout"
            -2147482645 -> "invalid value"
            else -> "code=$raw"
        }

        private fun intToBool(v: Int?): Boolean? = when (v) {
            null -> null
            0 -> false
            else -> true
        }

        private fun plausibleTemp(v: Int?): Int? = v?.takeIf { it in -40..50 }

        private fun fmtTemp(v: Int?): String = if (v == null) "—" else "$v°C"

        private fun fmtOnOff(v: Boolean?): String = when (v) {
            true -> "ON"
            false -> "OFF"
            null -> "—"
        }

        private fun fmtControl(v: Int?): String = when (v) {
            0 -> "Auto"
            1 -> "Manual"
            null -> "—"
            else -> v.toString()
        }

        private fun fmtCycle(v: Int?): String = when (v) {
            0 -> "Recirc"
            1 -> "Fresh"
            null -> "—"
            else -> v.toString()
        }
    }
}

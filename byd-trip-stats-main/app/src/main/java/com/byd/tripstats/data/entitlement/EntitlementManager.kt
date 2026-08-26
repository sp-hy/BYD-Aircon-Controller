package com.byd.tripstats.data.entitlement

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Outcome of redeeming an unlock code, for precise UI feedback. */
enum class RedeemResult { SUCCESS, INVALID, NO_VEHICLE_YET, UNAVAILABLE }

/**
 * Single source of truth for premium ("Pro") entitlement — mirrors the
 * `RuntimeExtensionBridge` pattern: everything else just asks [isPro] / [isProNow].
 *
 * Pro is unlocked by a short per-vehicle code (see [ProLicenseBridge] /
 * `ProLicenseHooks`), derived from the vehicle id via HMAC. The redeemed code is
 * persisted and **re-checked on every launch** against the code expected for the
 * current vehicle, so it grants Pro only on the car it was minted for and survives
 * an app reinstall. The vehicle id arrives asynchronously from telemetry via
 * [onDeviceIdObserved] and is cached so Pro re-activates without flicker.
 *
 * Synchronous [isProNow] is provided for hot paths like the telemetry loop; UI
 * observes the flows. Initialised once from
 * [com.byd.tripstats.BydStatsApplication.onCreate], before any Activity or Service.
 */
object EntitlementManager {

    private const val TAG = "EntitlementManager"
    private const val PREFS = "entitlement"
    private const val KEY_CODE = "unlock_code"
    private const val KEY_DEVICE_ID = "last_device_id"

    private val _isPro = MutableStateFlow(false)
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    private val _currentDeviceId = MutableStateFlow<String?>(null)
    /** Current vehicle's BYD id (the "Vehicle ID" the buyer sends), or null if not seen yet. */
    val currentDeviceId: StateFlow<String?> = _currentDeviceId.asStateFlow()

    private val _hasSavedCode = MutableStateFlow(false)
    /** True when a code is stored (whether or not it unlocks the current vehicle). */
    val hasSavedCode: StateFlow<Boolean> = _hasSavedCode.asStateFlow()

    private var storedCode: String? = null
    private var appContext: Context? = null

    /** Synchronous Pro check for hot paths (e.g. the per-tick telemetry loop). */
    fun isProNow(): Boolean = _isPro.value

    /** Idempotent. Loads the cached vehicle id and any saved unlock code. */
    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        _currentDeviceId.value = normalize(prefs().getString(KEY_DEVICE_ID, null))
        storedCode = prefs().getString(KEY_CODE, null)
        recompute()
        Log.i(TAG, "Entitlement initialised: pro=${_isPro.value}")
    }

    /**
     * Fed from telemetry whenever the vehicle id is observed. Caches it and
     * re-evaluates Pro (a saved code activates once its car is confirmed).
     */
    fun onDeviceIdObserved(rawId: String?) {
        if (appContext == null) return
        val id = normalize(rawId) ?: return
        if (id.equals(_currentDeviceId.value, ignoreCase = true)) return
        _currentDeviceId.value = id
        prefs().edit().putString(KEY_DEVICE_ID, id).apply()
        recompute()
        Log.i(TAG, "Vehicle id observed; pro=${_isPro.value}")
    }

    /** Check [rawCode] against the current vehicle; on success persist it and grant Pro. */
    fun redeem(rawCode: String): RedeemResult {
        val vehicleId = _currentDeviceId.value ?: return RedeemResult.NO_VEHICLE_YET
        val expected = ProLicenseBridge.expectedCode(vehicleId) ?: return RedeemResult.UNAVAILABLE
        if (!LicenseCode.matches(rawCode, expected)) return RedeemResult.INVALID
        storedCode = LicenseCode.normalizeInput(rawCode)
        prefs().edit().putString(KEY_CODE, storedCode).apply()
        recompute()
        Log.i(TAG, "Unlock code accepted")
        return RedeemResult.SUCCESS
    }

    /** Remove the saved code and drop back to the free tier. */
    fun clear() {
        storedCode = null
        prefs().edit().remove(KEY_CODE).apply()
        recompute()
    }

    private fun recompute() {
        val vehicleId = _currentDeviceId.value
        val code = storedCode
        _isPro.value = vehicleId != null && code != null &&
            LicenseCode.matches(code, ProLicenseBridge.expectedCode(vehicleId))
        _hasSavedCode.value = code != null
    }

    private fun normalize(s: String?): String? = s?.trim()?.takeIf { it.isNotEmpty() }

    private fun prefs(): SharedPreferences =
        requireNotNull(appContext) { "EntitlementManager.init() not called" }
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

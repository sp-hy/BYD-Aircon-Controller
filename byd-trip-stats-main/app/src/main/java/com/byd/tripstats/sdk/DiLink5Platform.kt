package com.byd.tripstats.sdk

import android.os.Build
import android.util.Log
import com.byd.tripstats.BuildConfig

/**
 * Detects whether we are running on a DiLink-5 head unit (Sealion 7 etc., `ro.vehicle.type=Di5*`,
 * Android 11 / SDK 30) vs the DiLink-3 platform the app was originally built for.
 *
 * The data-source layer uses this to choose the DiLink-5 code path (typed AbsBYDAuto*Listener +
 * feature-ID event stream) over the DiLink-3 path. The real DiLink-5 bydauto SDK is bundled only
 * in the `dilink5` product flavor; on a `dilink3` build this returns false on D3 hardware and the
 * D5 hooks are simply absent.
 *
 * Shared (src/main) so both flavors compile — it touches no bydauto types.
 */
object DiLink5Platform {
    private const val TAG = "DiLink5Platform"

    val vehicleType: String by lazy { systemProp("ro.vehicle.type") }

    /**
     * True on DiLink-5 hardware — detected solely by `ro.vehicle.type` starting with "Di5".
     * No SDK-version fallback: an SDK>=30 check would misdetect a newer-Android DiLink-3 unit that
     * doesn't expose `ro.vehicle.type` as D5.
     */
    val isDiLink5: Boolean by lazy {
        val result = vehicleType.startsWith("Di5", ignoreCase = true)
        Log.i(TAG, "isDiLink5=$result (ro.vehicle.type='$vehicleType', sdk=${Build.VERSION.SDK_INT})")
        result
    }

    /** The product flavor this hardware expects: "dilink5" on DiLink-5, "dilink3" otherwise. */
    val expectedFlavor: String get() = if (isDiLink5) "dilink5" else "dilink3"

    /**
     * True only when the installed build genuinely can't drive this hardware: a **DiLink-5 car
     * running a non-dilink5 build**, which has no DiLink-5 SDK path at all (no Dilink5Client, no
     * injector) → no telemetry.
     *
     * The reverse — a dilink5 build on a DiLink-3 car — is deliberately NOT flagged: it works,
     * because the D3 telemetry is read through the signature-agnostic RuntimeExtensionBridge and
     * the D5-only typed code (the sole user of drifted signatures like getTotalMileageValue) is
     * gated off by isDiLink5. Always false when the app has no product flavors (FLAVOR == "").
     */
    val isBuildUnsupportedForHardware: Boolean
        get() = isDiLink5 && BuildConfig.FLAVOR.isNotEmpty() && BuildConfig.FLAVOR != "dilink5"

    /** Human-readable label for a flavor, e.g. "DiLink 5" / "DiLink 3". */
    fun flavorLabel(flavor: String): String = if (flavor == "dilink5") "DiLink 5" else "DiLink 3"

    private fun systemProp(key: String): String = try {
        @Suppress("PrivateApi")
        val sp = Class.forName("android.os.SystemProperties")
        (sp.getMethod("get", String::class.java).invoke(null, key) as? String).orEmpty()
    } catch (t: Throwable) {
        ""
    }
}

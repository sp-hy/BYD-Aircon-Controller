package com.byd.tripstats.data.entitlement

import android.util.Log

/**
 * Reflective bridge to the private Pro-code derivation
 * (`com.byd.tripstats.runtime.ProLicenseHooks` in the closed-source telemetry
 * module). Mirrors [com.byd.tripstats.runtimebridge.RuntimeExtensionBridge].
 *
 * The HMAC secret and the code algorithm live in the private module, absent from
 * the public source. When the module is missing (a public-source-only build),
 * [expectedCode] returns null and Pro can never be unlocked.
 */
object ProLicenseBridge {
    private const val TAG = "ProLicenseBridge"
    private const val HOOK_CLASS = "com.byd.tripstats.runtime.ProLicenseHooks"

    private val hookClass: Class<*>? by lazy {
        runCatching { Class.forName(HOOK_CLASS) }.getOrNull()
    }

    /** True when the private code derivation is present in this build. */
    val isAvailable: Boolean
        get() = hookClass != null

    /**
     * @return the canonical unlock code for [vehicleId] + [tier], or null if the
     *         vehicle id is blank or the private module is absent.
     */
    fun expectedCode(vehicleId: String, tier: String = "pro"): String? {
        val clazz = hookClass ?: return null
        return runCatching {
            clazz.getMethod("expectedCode", String::class.java, String::class.java)
                .invoke(null, vehicleId, tier) as? String
        }.onFailure { Log.w(TAG, "expectedCode failed: ${it.message}") }
            .getOrNull()
    }
}

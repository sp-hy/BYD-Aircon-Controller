package com.byd.tripstats.runtimebridge

import android.content.Context
import android.util.Log

/**
 * Optional bridge into a gitignored runtime extension.
 *
 * Keep this public-side API intentionally tiny. The app must continue to compile
 * and run when the extension is absent from the checkout.
 */
object RuntimeExtensionBridge {
    private const val TAG = "RuntimeExtensionBridge"
    private const val HOOK_CLASS = "com.byd.tripstats.runtime.RuntimeExtensionHooks"

    data class VendorLogSignal(
        val kind: String,
        val source: String,
        val inCar: Int? = null,
        val outCar: Int? = null,
        val raw: Int? = null,
        val summary: String? = null
    )

    private val hookClass: Class<*>? by lazy {
        runCatching { Class.forName(HOOK_CLASS) }.getOrNull()
    }

    val isAvailable: Boolean
        get() = hookClass != null

    fun describe(): String {
        return invokeString("describe") ?: "optional module unavailable"
    }

    /** Earliest process-start hook. Must be the first thing called in Application.onCreate. */
    fun prime(): Boolean {
        return runCatching { hookClass?.getMethod("prime")?.invoke(null) as? Boolean }
            .onFailure { Log.w(TAG, "prime failed: ${it.message}") }
            .getOrNull() ?: false
    }

    /** Register this client with the vehicle data-cache service. Re-run on each process start. */
    fun registerDataCache(context: Context): Boolean {
        return runCatching {
            hookClass?.getMethod("registerDataCache", Any::class.java)
                ?.invoke(null, context.applicationContext) as? Boolean
        }.onFailure { Log.w(TAG, "registerDataCache failed: ${it.message}") }
            .getOrNull() ?: false
    }

    /** Add the package to the persistent ACC-mode whitelist. Re-run on each process start. */
    fun whitelistAcc(context: Context): Boolean {
        return runCatching {
            hookClass?.getMethod("whitelistAcc", Any::class.java)
                ?.invoke(null, context.applicationContext) as? Boolean
        }.onFailure { Log.w(TAG, "whitelistAcc failed: ${it.message}") }
            .getOrNull() ?: false
    }

    fun doubleValue(key: String, fallback: Double): Double {
        return runCatching {
            hookClass
                ?.getMethod("doubleValue", String::class.java, Double::class.javaPrimitiveType)
                ?.invoke(null, key, fallback) as? Double
        }.onFailure {
            Log.w(TAG, "Optional hook doubleValue failed: ${it.message}")
        }.getOrNull() ?: fallback
    }

    fun intValue(key: String, context: Context, fallback: Int): Int {
        return runCatching {
            hookClass
                ?.getMethod("intValue", String::class.java, Any::class.java, Int::class.javaPrimitiveType)
                ?.invoke(null, key, context.applicationContext, fallback) as? Int
        }.onFailure {
            Log.w(TAG, "Optional hook intValue failed: ${it.message}")
        }.getOrNull() ?: fallback
    }

    fun booleanValue(key: String, context: Context, fallback: Boolean): Boolean {
        return runCatching {
            hookClass
                ?.getMethod("booleanValue", String::class.java, Any::class.java, Boolean::class.javaPrimitiveType)
                ?.invoke(null, key, context.applicationContext, fallback) as? Boolean
        }.onFailure {
            Log.w(TAG, "Optional hook booleanValue failed: ${it.message}")
        }.getOrNull() ?: fallback
    }

    fun onDataSourceStarted(context: Context) {
        if (invoke(
            methodName = "onDataSourceStarted",
            parameterTypes = arrayOf(Context::class.java),
            args = arrayOf(context.applicationContext),
            warnIfMissing = false
        )) return
        invoke(
            methodName = "onDataSourceStarted",
            parameterTypes = arrayOf(Any::class.java),
            args = arrayOf(context.applicationContext)
        )
    }

    fun onDataSourceStopped() {
        invoke("onDataSourceStopped")
    }

    fun applyStartupSafeguards(context: Context) {
        if (invoke(
                methodName = "applyStartupSafeguards",
                parameterTypes = arrayOf(Context::class.java),
                args = arrayOf(context.applicationContext),
                warnIfMissing = false
            )
        ) return
        invoke(
            methodName = "applyStartupSafeguards",
            parameterTypes = arrayOf(Any::class.java),
            args = arrayOf(context.applicationContext),
            warnIfMissing = false
        )
    }

    fun vendorLogcatFilters(): List<String> {
        return runCatching {
            @Suppress("UNCHECKED_CAST")
            hookClass?.getMethod("vendorLogcatFilters")?.invoke(null) as? List<String>
        }.onFailure {
            Log.w(TAG, "Optional hook vendorLogcatFilters failed: ${it.message}")
        }.getOrNull().orEmpty()
    }

    fun parseVendorLogLine(line: String): VendorLogSignal? {
        val parsed = runCatching {
            hookClass
                ?.getMethod("parseVendorLogLine", String::class.java)
                ?.invoke(null, line) as? Map<*, *>
        }.onFailure {
            Log.w(TAG, "Optional hook parseVendorLogLine failed: ${it.message}")
        }.getOrNull() ?: return null

        return VendorLogSignal(
            kind = parsed["kind"] as? String ?: return null,
            source = parsed["source"] as? String ?: "optional-log",
            inCar = parsed["inCar"] as? Int,
            outCar = parsed["outCar"] as? Int,
            raw = parsed["raw"] as? Int,
            summary = parsed["summary"] as? String
        )
    }

    fun methodGroups(key: String): List<Pair<String, List<String>>> {
        val parsed = runCatching {
            @Suppress("UNCHECKED_CAST")
            hookClass
                ?.getMethod("methodGroups", String::class.java)
                ?.invoke(null, key) as? Map<String, List<String>>
        }.onFailure {
            Log.w(TAG, "Optional hook methodGroups failed: ${it.message}")
        }.getOrNull() ?: return emptyList()

        return parsed.map { (label, methodNames) -> label to methodNames }
    }

    fun intGroup(key: String): IntArray {
        return runCatching {
            hookClass
                ?.getMethod("intGroup", String::class.java)
                ?.invoke(null, key) as? IntArray
        }.onFailure {
            Log.w(TAG, "Optional hook intGroup failed: ${it.message}")
        }.getOrNull() ?: IntArray(0)
    }

    fun labeledIntGroup(key: String): Map<String, Int> {
        return runCatching {
            @Suppress("UNCHECKED_CAST")
            hookClass
                ?.getMethod("labeledIntGroup", String::class.java)
                ?.invoke(null, key) as? Map<String, Int>
        }.onFailure {
            Log.w(TAG, "Optional hook labeledIntGroup failed: ${it.message}")
        }.getOrNull().orEmpty()
    }

    fun stringList(key: String, arg: String = ""): List<String> {
        return runCatching {
            @Suppress("UNCHECKED_CAST")
            hookClass
                ?.getMethod("stringList", String::class.java, String::class.java)
                ?.invoke(null, key, arg) as? List<String>
        }.onFailure {
            Log.w(TAG, "Optional hook stringList failed: ${it.message}")
        }.getOrNull().orEmpty()
    }

    fun stringMap(key: String, arg: String = ""): Map<String, String> {
        return runCatching {
            @Suppress("UNCHECKED_CAST")
            hookClass
                ?.getMethod("stringMap", String::class.java, String::class.java)
                ?.invoke(null, key, arg) as? Map<String, String>
        }.onFailure {
            Log.w(TAG, "Optional hook stringMap failed: ${it.message}")
        }.getOrNull().orEmpty()
    }

    fun applyPatches(context: Context): Map<String, Boolean> {
        return runCatching {
            @Suppress("UNCHECKED_CAST")
            hookClass
                ?.getMethod("applyPatches", Any::class.java)
                ?.invoke(null, context.applicationContext) as? Map<String, Boolean>
        }.onFailure {
            Log.w(TAG, "Optional hook applyPatches failed: ${it.message}")
        }.getOrNull().orEmpty()
    }

    private fun invokeString(methodName: String): String? {
        return runCatching {
            hookClass?.getMethod(methodName)?.invoke(null) as? String
        }.onFailure {
            Log.w(TAG, "Optional hook $methodName failed: ${it.message}")
        }.getOrNull()
    }

    private fun invoke(
        methodName: String,
        parameterTypes: Array<Class<*>> = emptyArray(),
        args: Array<Any> = emptyArray(),
        warnIfMissing: Boolean = true
    ): Boolean {
        val clazz = hookClass ?: return false
        return runCatching {
            clazz.getMethod(methodName, *parameterTypes).invoke(null, *args)
            true
        }.onFailure {
            if (warnIfMissing || it !is NoSuchMethodException) {
                Log.w(TAG, "Optional hook $methodName failed: ${it.message}")
            }
        }.getOrDefault(false)
    }
}

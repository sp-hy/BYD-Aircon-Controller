package com.byd.tripstats.util

import android.content.Context
import android.util.Log
import com.byd.tripstats.runtimebridge.RuntimeExtensionBridge

internal object RtInProcessPatches {
    private const val TAG = "RtInProcessPatches"

    fun apply(context: Context) {
        val results = RuntimeExtensionBridge.applyPatches(context)
        if (results.isEmpty()) {
            Log.d(TAG, "optional module unavailable — skipping")
            return
        }
        val ok = results.values.count { it }
        Log.i(TAG, "done: $ok/${results.size} — $results")
    }
}

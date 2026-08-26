package com.byd.tripstats.util

import android.content.Context
import android.util.Log
import com.byd.tripstats.adb.AdbPermissionManager
import com.byd.tripstats.runtimebridge.RuntimeExtensionBridge

internal object RtShellPatches {
    private const val TAG = "RtShellPatches"
    private const val PKG = "com.byd.tripstats"

    suspend fun apply(context: Context) {
        if (!AdbPermissionManager.isSetupComplete(context)) return
        val commands = RuntimeExtensionBridge.stringList("sp01", PKG)
        if (commands.isEmpty()) return
        Log.i(TAG, "applying ${commands.size} patches")
        val results = AdbPermissionManager.runShellBatch(
            context,
            commands,
            perCommandTimeoutMs = 5_000L,
        )
        if (results.isEmpty()) {
            Log.w(TAG, "channel unreachable")
            return
        }
        var ok = 0
        for ((cmd, res) in commands.zip(results)) {
            val success = res.exitCode == 0 && !res.output.contains("Error:", ignoreCase = false)
            if (success) ok++
            val snippet = res.output.replace('\n', ' ').take(120)
            Log.d(TAG, "[${if (success) "ok" else "fail:${res.exitCode}"}] $cmd :: $snippet")
        }
        Log.i(TAG, "done: $ok/${commands.size}")
    }
}

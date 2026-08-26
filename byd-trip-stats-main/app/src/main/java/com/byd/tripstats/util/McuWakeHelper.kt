package com.byd.tripstats.util

import android.content.Context
import com.byd.tripstats.runtimebridge.RuntimeExtensionBridge

object McuWakeHelper {
    fun keepAlive(context: Context): Boolean {
        return RuntimeExtensionBridge.booleanValue("b01", context, fallback = false)
    }

    fun getMcuStatus(context: Context): Int {
        return RuntimeExtensionBridge.intValue("n01", context, fallback = -1)
    }
}

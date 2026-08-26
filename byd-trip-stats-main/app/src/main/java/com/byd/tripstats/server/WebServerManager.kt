package com.byd.tripstats.server

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.byd.tripstats.data.local.BydStatsDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.net.NetworkInterface

object WebServerManager {

    private const val TAG = "WebServerManager"

    private var server: LocalWebServer? = null

    val isRunning: Boolean get() = server?.isAlive == true

    private val _lockedOutCount = MutableStateFlow(0)
    val lockedOutCount: StateFlow<Int> = _lockedOutCount.asStateFlow()

    fun clearLockouts() {
        server?.clearLockouts()
    }

    /** Returns null on success, or an error message string if the server failed to bind. */
    fun start(
        context: Context,
        port: Int = LocalWebServer.DEFAULT_PORT,
        pin: String
    ): String? {
        if (isRunning) return null
        _lockedOutCount.value = 0
        val db = BydStatsDatabase.getDatabase(context.applicationContext)
        val s  = LocalWebServer(context.applicationContext, db, port, pin) { count ->
            _lockedOutCount.value = count
        }
        return try {
            s.start(NanoHTTPDReadTimeout, /* daemon= */ true)
            server = s
            Log.i(TAG, "Started on port $port")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start on port $port", e)
            e.message ?: "Failed to start server"
        }
    }

    fun stop() {
        server?.stop()
        server = null
        _lockedOutCount.value = 0
        Log.i(TAG, "Stopped")
    }

    fun getUrl(context: Context): String? {
        val ip   = getLocalIpAddress(context) ?: return null
        val port = server?.listeningPort ?: return null
        return "http://$ip:$port"
    }

    private fun getLocalIpAddress(context: Context): String? {
        // Try NetworkInterface first (works on most Android versions)
        try {
            val iface = NetworkInterface.getNetworkInterfaces()
                ?.asSequence()
                ?.flatMap { it.inetAddresses.asSequence() }
                ?.firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
            if (iface != null) return iface.hostAddress
        } catch (_: Exception) {}

        // Fallback: WifiManager
        return try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            val ip = wm.connectionInfo.ipAddress
            if (ip == 0) null
            else "${ip and 0xff}.${(ip shr 8) and 0xff}.${(ip shr 16) and 0xff}.${(ip shr 24) and 0xff}"
        } catch (_: Exception) { null }
    }

    // NanoHTTPD socket read timeout (ms); 5 s is a safe default
    private const val NanoHTTPDReadTimeout = 5_000
}

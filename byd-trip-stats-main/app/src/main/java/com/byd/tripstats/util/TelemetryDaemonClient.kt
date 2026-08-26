package com.byd.tripstats.util

import android.util.Log
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Phase 3 — app-side client for the privileged telemetry daemon (TelemetryDaemonMain).
 *
 * Connects to 127.0.0.1:DAEMON_PORT, reads newline-delimited JSON snapshots, and hands the parsed
 * values to [onTelemetry]. The daemon supplies clean, instant speed/gear that the normal app process
 * cannot obtain itself (see private-telemetry/INSTANT_TELEMETRY_PLAN.md). Runs on a daemon thread and
 * reconnects with a fixed backoff, so it tolerates the daemon starting late or restarting. Failure is
 * silent — if the daemon isn't running the app simply falls back to its in-process poll/inference.
 */
class TelemetryDaemonClient(
    private val onTelemetry: (speedKmh: Double?, gear: String?, powerKw: Double?, frontRpm: Int?, rearRpm: Int?) -> Unit
) {
    @Volatile private var running = false
    private var thread: Thread? = null

    fun start() {
        if (running) return
        running = true
        thread = Thread({ loop() }, "telemetry-daemon-client").apply { isDaemon = true; start() }
    }

    fun stop() {
        running = false
        thread?.interrupt()
        thread = null
    }

    private fun loop() {
        while (running) {
            try {
                Socket().use { sock ->
                    sock.connect(InetSocketAddress("127.0.0.1", TelemetryDaemonMain.DAEMON_PORT), 3000)
                    sock.tcpNoDelay = true
                    Log.i(TAG, "connected to telemetry daemon")
                    val reader = sock.getInputStream().bufferedReader()
                    while (running) {
                        val line = reader.readLine() ?: break
                        parse(line)
                    }
                }
            } catch (_: InterruptedException) {
                return
            } catch (_: Exception) {
                // daemon not up yet / connection dropped — retry after backoff
            }
            if (running) {
                try { Thread.sleep(RECONNECT_MS) } catch (_: InterruptedException) { return }
            }
        }
    }

    private fun parse(line: String) {
        try {
            val o = JSONObject(line)
            val speed = if (o.has("speedKmh")) o.getDouble("speedKmh") else null
            val gear = if (o.has("gear")) o.getString("gear") else null
            val power = if (o.has("powerKw")) o.getDouble("powerKw") else null
            val frontRpm = if (o.has("frontRpm")) o.getInt("frontRpm") else null
            val rearRpm = if (o.has("rearRpm")) o.getInt("rearRpm") else null
            if (speed != null || gear != null || power != null || frontRpm != null || rearRpm != null)
                onTelemetry(speed, gear, power, frontRpm, rearRpm)
        } catch (_: Exception) { /* ignore malformed line */ }
    }

    companion object {
        private const val TAG = "TelemetryDaemonClient"
        private const val RECONNECT_MS = 3000L
    }
}

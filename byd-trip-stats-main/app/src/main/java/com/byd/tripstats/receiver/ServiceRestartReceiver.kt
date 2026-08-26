package com.byd.tripstats.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.byd.tripstats.service.ServiceRestarterJobService
import com.byd.tripstats.service.VehicleTelemetryService
import com.byd.tripstats.util.ServiceIdleState

/**
 * Lightweight restart bridge used when the foreground vehicle service is removed
 * from the recent-task stack or destroyed unexpectedly.
 *
 * Skips the restart while [ServiceIdleState.isStayingIdle] — pending restart
 * alarms scheduled before the off-state self-stop must not undo it.
 */
class ServiceRestartReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "Restart receiver fired: ${intent.action}")
        if (ServiceIdleState.isStayingIdle(context.applicationContext)) {
            Log.i(TAG, "Restart skipped — service in off-state idle")
            return
        }
        try {
            VehicleTelemetryService.start(context.applicationContext)
            ServiceRestarterJobService.schedulePeriodic(context.applicationContext, "alarm-restart")
            Log.i(TAG, "✅ Vehicle telemetry restart dispatched")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to restart vehicle telemetry service", e)
        }
    }

    companion object {
        private const val TAG = "ServiceRestartRcvr"
        const val ACTION_RESTART_VEHICLE_TELEMETRY_SERVICE =
            "com.byd.tripstats.action.RESTART_VEHICLE_TELEMETRY_SERVICE"

        fun schedule(context: Context, delayMs: Long, reason: String) {
            runCatching {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                    ?: return
                val restartIntent = Intent(context, ServiceRestartReceiver::class.java).apply {
                    action = ACTION_RESTART_VEHICLE_TELEMETRY_SERVICE
                    putExtra("reason", reason)
                    putExtra("scheduled_at", System.currentTimeMillis())
                }
                // Unique request code and URI per (reason, delay) pair so that
                // concurrent alarms (15s, 45s, 120s boot kicks + crash restarts)
                // all coexist in AlarmManager without overwriting each other.
                val requestCode = (reason.hashCode() * 31 + delayMs.hashCode()).let {
                    // Keep positive to avoid PendingIntent confusion
                    if (it < 0) it.and(0x7FFFFFFF) else it
                }
                restartIntent.data = Uri.parse("bydstats://restart/$requestCode")
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    restartIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val triggerAt = System.currentTimeMillis() + delayMs
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pendingIntent
                )
                Log.i(TAG, "Scheduled vehicle telemetry restart in ${delayMs}ms ($reason)")
            }.onFailure { error ->
                Log.e(TAG, "Failed to schedule vehicle telemetry restart ($reason)", error)
            }
        }
    }
}
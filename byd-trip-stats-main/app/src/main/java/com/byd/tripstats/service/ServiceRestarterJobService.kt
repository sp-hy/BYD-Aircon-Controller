package com.byd.tripstats.service

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.PersistableBundle
import android.util.Log
import com.byd.tripstats.util.ServiceIdleState
import java.util.concurrent.TimeUnit

/**
 * Secondary restart lane that survives process death and complements the alarm
 * receiver / WorkManager watchdog path.
 */
class ServiceRestarterJobService : JobService() {

    override fun onStartJob(params: JobParameters): Boolean {
        val reason = params.extras.getString(EXTRA_REASON) ?: "unknown"
        if (ServiceIdleState.isStayingIdle(applicationContext)) {
            Log.i(TAG, "JobService skipped — service in off-state idle (jobId=${params.jobId} reason=$reason)")
            jobFinished(params, false)
            return false
        }
        Log.i(TAG, "JobService fired: jobId=${params.jobId} reason=$reason")
        return try {
            VehicleTelemetryService.start(applicationContext)
            Log.i(TAG, "✅ JobService restart dispatched")
            jobFinished(params, false)
            false
        } catch (e: Exception) {
            Log.e(TAG, "❌ JobService restart failed", e)
            jobFinished(params, true)
            false
        }
    }

    override fun onStopJob(params: JobParameters): Boolean {
        Log.w(TAG, "JobService interrupted: jobId=${params.jobId}")
        return true
    }

    companion object {
        private const val TAG = "ServiceRestartJob"
        private const val EXTRA_REASON = "reason"

        private const val PERIODIC_JOB_ID = 20_001
        private const val EARLY_KICK_JOB_ID = 20_002
        private const val LATE_KICK_JOB_ID = 20_003

        private const val PERIODIC_INTERVAL_MINUTES = 15L

        fun schedulePeriodic(context: Context, reason: String) {
            schedule(
                context = context,
                jobId = PERIODIC_JOB_ID,
                reason = reason,
                persisted = true
            ) { builder ->
                builder.setPeriodic(TimeUnit.MINUTES.toMillis(PERIODIC_INTERVAL_MINUTES))
            }
        }

        fun scheduleEarlyKick(context: Context, delayMs: Long, reason: String) {
            scheduleKick(context, EARLY_KICK_JOB_ID, delayMs, reason)
        }

        fun scheduleLateKick(context: Context, delayMs: Long, reason: String) {
            scheduleKick(context, LATE_KICK_JOB_ID, delayMs, reason)
        }

        private fun scheduleKick(context: Context, jobId: Int, delayMs: Long, reason: String) {
            schedule(
                context = context,
                jobId = jobId,
                reason = reason,
                persisted = false
            ) { builder ->
                builder
                    .setMinimumLatency(delayMs)
                    .setOverrideDeadline(delayMs + 5_000L)
            }
        }

        private fun schedule(
            context: Context,
            jobId: Int,
            reason: String,
            persisted: Boolean,
            configure: (JobInfo.Builder) -> JobInfo.Builder
        ) {
            runCatching {
                val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler
                    ?: return
                val extras = PersistableBundle().apply {
                    putString(EXTRA_REASON, reason)
                }
                val component = ComponentName(context, ServiceRestarterJobService::class.java)
                val builder = JobInfo.Builder(jobId, component)
                    .setPersisted(persisted)
                    .setExtras(extras)
                    .setRequiresCharging(false)
                    .setRequiresDeviceIdle(false)
                val result = scheduler.schedule(configure(builder).build())
                if (result == JobScheduler.RESULT_SUCCESS) {
                    Log.i(TAG, "Scheduled jobId=$jobId persisted=$persisted ($reason)")
                } else {
                    Log.w(TAG, "JobScheduler rejected jobId=$jobId ($reason), result=$result")
                }
            }.onFailure { error ->
                Log.e(TAG, "Failed to schedule jobId=$jobId ($reason)", error)
            }
        }

        fun cancelPeriodic(context: Context) {
            runCatching {
                val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler
                    ?: return
                scheduler.cancel(PERIODIC_JOB_ID)
                Log.i(TAG, "Cancelled periodic JobService (jobId=$PERIODIC_JOB_ID, off-state idle)")
            }.onFailure { Log.w(TAG, "cancelPeriodic failed: ${it.message}") }
        }
    }
}

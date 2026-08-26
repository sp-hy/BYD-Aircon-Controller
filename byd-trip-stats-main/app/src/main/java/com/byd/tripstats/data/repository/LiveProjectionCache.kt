package com.byd.tripstats.data.repository

import android.content.Context
import android.util.Log
import com.byd.tripstats.ui.components.RangeDataPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Process-lifetime, disk-backed cache of the live range-projection curve for the
 * in-progress trip.
 *
 * **Why this exists.** The projection curve (`DashboardViewModel._tripDataPoints`)
 * is built tick-by-tick in the *Activity-scoped* ViewModel, so it is lost whenever
 * the Activity is finished (back press) or the process is killed. On the next open
 * the ViewModel rebuilds the curve from the persisted data points — but that
 * rebuild can only apply a single consumption rate to every historical point (it
 * cannot recover the rate that was live at each past instant), which both costs a
 * recompute and flattens the historical line.
 *
 * This cache keeps the *as-computed-live* curve so a reopen can show it verbatim:
 *  - **In memory** — survives Activity death (the common back-press case, while the
 *    foreground service keeps the process alive).
 *  - **On disk** — survives a cold start (process killed / Deep Sleep), flushed on
 *    a throttle so it costs next to nothing per tick.
 *
 * **It is purely a display aid.** It never feeds the live accumulators, anchors, or
 * the projection math — `restoreTripState` still derives all of those from the
 * database exactly as before — so a stale, partial, or missing cache can only ever
 * fall back to today's rebuild, never corrupt live tracking. See
 * [com.byd.tripstats.ui.viewmodel.DashboardViewModel.mergeProjectionCurve] for how
 * the cached head and the rebuilt tail are stitched together without gaps.
 */
class LiveProjectionCache private constructor(context: Context) {

    @Serializable
    private data class Snapshot(val tripId: Long, val points: List<RangeDataPoint>)

    private val file = File(context.filesDir, FILE_NAME)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile private var memTripId: Long? = null
    @Volatile private var memPoints: List<RangeDataPoint> = emptyList()

    // A single in-flight flush coalesces the bursts of per-100m updates into one
    // write per FLUSH_INTERVAL_MS, so a long trip doesn't hammer storage.
    private var flushJob: Job? = null

    /** Record the latest curve in memory and schedule a throttled disk flush. */
    @Synchronized
    fun update(tripId: Long, points: List<RangeDataPoint>) {
        memTripId = tripId
        memPoints = points
        if (flushJob?.isActive == true) return
        flushJob = ioScope.launch {
            delay(FLUSH_INTERVAL_MS)
            flushToDisk()
        }
    }

    /**
     * The cached curve for [tripId], or null if nothing is cached for it. Prefers
     * the in-memory copy (Activity-death case) and falls back to disk (cold start).
     */
    fun load(tripId: Long): List<RangeDataPoint>? {
        memTripId?.let { if (it == tripId) return memPoints.takeIf { p -> p.isNotEmpty() } }
        val disk = runCatching {
            if (!file.exists()) return null
            json.decodeFromString<Snapshot>(file.readText())
        }.getOrElse {
            Log.w(TAG, "Failed to read live projection cache", it)
            null
        } ?: return null
        if (disk.tripId != tripId || disk.points.isEmpty()) return null
        // Warm the in-memory copy so subsequent reads skip disk.
        memTripId = disk.tripId
        memPoints = disk.points
        return disk.points
    }

    /** Forget the cached curve (trip ended / changed). */
    @Synchronized
    fun clear() {
        memTripId = null
        memPoints = emptyList()
        flushJob?.cancel()
        flushJob = null
        ioScope.launch { runCatching { if (file.exists()) file.delete() } }
    }

    @Synchronized
    private fun currentSnapshot(): Snapshot? {
        val id = memTripId ?: return null
        val pts = memPoints
        return if (pts.isEmpty()) null else Snapshot(id, pts)
    }

    private fun flushToDisk() {
        val snap = currentSnapshot() ?: return
        runCatching {
            val tmp = File(file.parentFile, "$FILE_NAME.tmp")
            tmp.writeText(json.encodeToString(snap))
            if (!tmp.renameTo(file)) {
                // rename can fail across some filesystems — fall back to a direct write.
                file.writeText(json.encodeToString(snap))
                tmp.delete()
            }
        }.onFailure { Log.w(TAG, "Failed to flush live projection cache", it) }
    }

    companion object {
        private const val TAG = "LiveProjectionCache"
        private const val FILE_NAME = "live_projection_cache.json"
        private const val FLUSH_INTERVAL_MS = 15_000L

        @Volatile private var INSTANCE: LiveProjectionCache? = null

        fun getInstance(context: Context): LiveProjectionCache =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: LiveProjectionCache(context.applicationContext).also { INSTANCE = it }
            }
    }
}

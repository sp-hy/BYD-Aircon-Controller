package com.byd.tripstats.util

import com.byd.tripstats.BuildConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Centralised backup filename scheme: `<prefix>_v<appVersion>_<timestamp>.db`
 *
 * The `v<appVersion>` segment records which build produced a backup, so a restored
 * `.db` can be matched to the schema it was written against. Nothing parses this name
 * back — every scan/sort/prune path keys off the file's `lastModified()` time — so the
 * layout can change freely without breaking restore.
 */
object BackupNaming {
    const val EXTENSION = ".db"

    /** App version, sanitised to filename-safe characters (e.g. "2.11.1-beta09"). */
    val appVersionTag: String
        get() = BuildConfig.VERSION_NAME.replace(Regex("[^A-Za-z0-9._-]"), "-")

    /** Minute-resolution timestamp, matching the existing backup naming. */
    fun timestamp(): String =
        SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault()).format(Date())

    fun fileName(prefix: String = "byd_stats_backup", timestamp: String = timestamp()): String =
        "${prefix}_v${appVersionTag}_$timestamp$EXTENSION"
}

package com.byd.tripstats.ui

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration

/**
 * True when the app is running in a narrow window (e.g. head-unit split-screen) — this
 * window's width is well under the physical display's long edge (full landscape width).
 * Device-independent: fullscreen ≈ 1.0, half-split ≈ 0.5.
 */
@Composable
fun rememberIsSplitScreen(): Boolean {
    val configuration = LocalConfiguration.current
    return remember(configuration.screenWidthDp) {
        val dm = Resources.getSystem().displayMetrics
        val fullDp = maxOf(dm.widthPixels, dm.heightPixels) / dm.density
        fullDp > 0f && configuration.screenWidthDp < fullDp * 0.72f
    }
}

package com.byd.tripstats.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Drives the full-screen white flash shown right after [ScreenshotUtil.captureAndSave]
 * grabs the frame. A singleton because the flash overlay is mounted once at the app
 * root (see MainActivity) while the trigger (tapping the BYD logo) can fire from any
 * screen's TopAppBar.
 */
object ScreenshotFlashController {
    private val _visible = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = _visible.asStateFlow()

    fun flash() {
        _visible.value = true
    }

    fun reset() {
        _visible.value = false
    }
}

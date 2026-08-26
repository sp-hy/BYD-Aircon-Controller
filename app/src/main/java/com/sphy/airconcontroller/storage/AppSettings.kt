package com.sphy.airconcontroller.storage

import android.content.Context

class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var espDeviceName: String
        get() = prefs.getString(KEY_ESP_DEVICE_NAME, DEFAULT_DEVICE_NAME) ?: DEFAULT_DEVICE_NAME
        set(value) {
            prefs.edit().putString(KEY_ESP_DEVICE_NAME, value).apply()
        }

    companion object {
        const val DEFAULT_DEVICE_NAME = "BYD-Aircon"
        private const val PREFS_NAME = "aircon_settings"
        private const val KEY_ESP_DEVICE_NAME = "esp_name"
    }
}

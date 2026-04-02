package com.sphy.airconcontroller.storage

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

data class Credentials(
    val email: String = "",
    val password: String = "",
    val controlPin: String = "",
    val countryCode: String = DEFAULT_COUNTRY_CODE,
    val baseUrl: String = DEFAULT_BASE_URL,
    val espDeviceName: String = DEFAULT_DEVICE_NAME
) {
    companion object {
        const val DEFAULT_COUNTRY_CODE = "AU"
        const val DEFAULT_BASE_URL = "https://dilinkappoversea-au.byd.auto"
        const val DEFAULT_DEVICE_NAME = "BYD-Aircon"
    }
}

class CredentialsStore(context: Context) {
    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "byd_credentials",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun save(credentials: Credentials) {
        sharedPreferences.edit()
            .putString(KEY_EMAIL, credentials.email)
            .putString(KEY_PASSWORD, credentials.password)
            .putString(KEY_PIN, credentials.controlPin)
            .putString(KEY_COUNTRY_CODE, credentials.countryCode)
            .putString(KEY_BASE_URL, credentials.baseUrl)
            .putString(KEY_ESP_DEVICE_NAME, credentials.espDeviceName)
            .apply()
    }

    fun load(): Credentials {
        return Credentials(
            email = sharedPreferences.getString(KEY_EMAIL, "") ?: "",
            password = sharedPreferences.getString(KEY_PASSWORD, "") ?: "",
            controlPin = sharedPreferences.getString(KEY_PIN, "") ?: "",
            countryCode = sharedPreferences.getString(KEY_COUNTRY_CODE, Credentials.DEFAULT_COUNTRY_CODE)
                ?: Credentials.DEFAULT_COUNTRY_CODE,
            baseUrl = sharedPreferences.getString(KEY_BASE_URL, Credentials.DEFAULT_BASE_URL)
                ?: Credentials.DEFAULT_BASE_URL,
            espDeviceName = sharedPreferences.getString(KEY_ESP_DEVICE_NAME, Credentials.DEFAULT_DEVICE_NAME)
                ?: Credentials.DEFAULT_DEVICE_NAME
        )
    }

    companion object {
        private const val KEY_EMAIL = "email"
        private const val KEY_PASSWORD = "password"
        private const val KEY_PIN = "control_pin"
        private const val KEY_COUNTRY_CODE = "country_code"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_ESP_DEVICE_NAME = "esp_name"
    }
}

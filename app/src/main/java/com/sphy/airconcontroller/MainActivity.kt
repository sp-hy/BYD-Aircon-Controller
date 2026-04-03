package com.sphy.airconcontroller

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.sphy.airconcontroller.ble.BleConnectionRegistry
import com.sphy.airconcontroller.ble.BleConnectionState
import com.sphy.airconcontroller.ble.toDisplayString
import com.sphy.airconcontroller.byd.BydApiClient
import com.sphy.airconcontroller.byd.BydConfig
import com.sphy.airconcontroller.service.AirconForegroundService
import com.sphy.airconcontroller.storage.Credentials
import com.sphy.airconcontroller.storage.CredentialsStore
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {
    private lateinit var store: CredentialsStore
    private lateinit var bydApiClient: BydApiClient
    private lateinit var bleStatusText: TextView

    private val bleStatusListener: (BleConnectionState) -> Unit = { state ->
        bleStatusText.text = state.toDisplayString(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        store = CredentialsStore(this)
        bydApiClient = BydApiClient(this)
        val current = store.load()

        val emailInput = findViewById<TextInputEditText>(R.id.emailInput)
        val passwordInput = findViewById<TextInputEditText>(R.id.passwordInput)
        val pinInput = findViewById<TextInputEditText>(R.id.pinInput)
        val countryCodeInput = findViewById<TextInputEditText>(R.id.countryCodeInput)
        val baseUrlInput = findViewById<TextInputEditText>(R.id.baseUrlInput)
        val deviceNameInput = findViewById<TextInputEditText>(R.id.deviceNameInput)
        bleStatusText = findViewById(R.id.bleStatusText)
        val saveButton = findViewById<Button>(R.id.saveButton)
        val testLoginButton = findViewById<Button>(R.id.testLoginButton)
        val turnAirconOnTestButton = findViewById<Button>(R.id.toggleAirconTestButton)
        val startServiceButton = findViewById<Button>(R.id.startServiceButton)
        val stopServiceButton = findViewById<Button>(R.id.stopServiceButton)

        emailInput.setText(current.email)
        passwordInput.setText(current.password)
        pinInput.setText(current.controlPin)
        countryCodeInput.setText(current.countryCode)
        baseUrlInput.setText(current.baseUrl)
        deviceNameInput.setText(current.espDeviceName)

        saveButton.setOnClickListener {
            val creds = Credentials(
                email = emailInput.text?.toString()?.trim().orEmpty(),
                password = passwordInput.text?.toString().orEmpty(),
                controlPin = pinInput.text?.toString()?.trim().orEmpty(),
                countryCode = countryCodeInput.text?.toString()?.trim().orEmpty(),
                baseUrl = baseUrlInput.text?.toString()?.trim().orEmpty(),
                espDeviceName = deviceNameInput.text?.toString()?.trim().orEmpty()
            )
            store.save(creds)
            Snackbar.make(saveButton, R.string.settings_saved, Snackbar.LENGTH_SHORT).show()
        }

        testLoginButton.setOnClickListener {
            val creds = Credentials(
                email = emailInput.text?.toString()?.trim().orEmpty(),
                password = passwordInput.text?.toString().orEmpty(),
                controlPin = pinInput.text?.toString()?.trim().orEmpty(),
                countryCode = countryCodeInput.text?.toString()?.trim().orEmpty(),
                baseUrl = baseUrlInput.text?.toString()?.trim().orEmpty(),
                espDeviceName = deviceNameInput.text?.toString()?.trim().orEmpty()
            )
            store.save(creds)
            thread(name = "test-login-worker") {
                val result = runCatching {
                    bydApiClient.login(
                        BydConfig(
                            username = creds.email,
                            password = creds.password,
                            controlPin = creds.controlPin,
                            countryCode = creds.countryCode,
                            baseUrl = creds.baseUrl
                        )
                    )
                }

                runOnUiThread {
                    if (result.isSuccess) {
                        Snackbar.make(testLoginButton, R.string.test_login_success, Snackbar.LENGTH_LONG).show()
                    } else {
                        val err = result.exceptionOrNull()
                        val details = err?.message ?: getString(R.string.error_unknown)
                        Snackbar.make(
                            testLoginButton,
                            getString(R.string.test_login_failed_with_reason, details),
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }

        turnAirconOnTestButton.setOnClickListener {
            val creds = Credentials(
                email = emailInput.text?.toString()?.trim().orEmpty(),
                password = passwordInput.text?.toString().orEmpty(),
                controlPin = pinInput.text?.toString()?.trim().orEmpty(),
                countryCode = countryCodeInput.text?.toString()?.trim().orEmpty(),
                baseUrl = baseUrlInput.text?.toString()?.trim().orEmpty(),
                espDeviceName = deviceNameInput.text?.toString()?.trim().orEmpty()
            )
            store.save(creds)
            thread(name = "aircon-on-test-worker") {
                val result = runCatching {
                    val config = BydConfig(
                        username = creds.email,
                        password = creds.password,
                        controlPin = creds.controlPin,
                        countryCode = creds.countryCode,
                        baseUrl = creds.baseUrl
                    )
                    bydApiClient.login(config)
                    val vin = bydApiClient.fetchVehicleList(config).firstOrNull()?.vin
                        ?.takeIf { it.isNotBlank() } ?: throw IllegalStateException("No VIN")
                    bydApiClient.verifyControlPassword(config, vin)
                    bydApiClient.startClimate(config, vin)
                }

                runOnUiThread {
                    val command = result.getOrNull()
                    if (command?.success == true) {
                        Snackbar.make(turnAirconOnTestButton, R.string.aircon_on_test_success, Snackbar.LENGTH_LONG)
                            .show()
                    } else {
                        val err = result.exceptionOrNull()
                        val details = err?.message ?: getString(R.string.error_unknown)
                        Snackbar.make(
                            turnAirconOnTestButton,
                            getString(R.string.toggle_aircon_test_failed_with_reason, details),
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }

        startServiceButton.setOnClickListener {
            if (!hasBluetoothPermissions()) {
                requestBluetoothPermissions()
                Snackbar.make(it, R.string.missing_permissions, Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            ContextCompat.startForegroundService(this, Intent(this, AirconForegroundService::class.java))
            Snackbar.make(it, R.string.service_running, Snackbar.LENGTH_SHORT).show()
        }

        stopServiceButton.setOnClickListener {
            stopService(Intent(this, AirconForegroundService::class.java))
            Snackbar.make(it, R.string.service_stopped, Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun onStart() {
        super.onStart()
        BleConnectionRegistry.addListener(bleStatusListener)
    }

    override fun onStop() {
        BleConnectionRegistry.removeListener(bleStatusListener)
        super.onStop()
    }

    private fun hasBluetoothPermissions(): Boolean {
        val perms = requiredPermissions()
        return perms.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
    }

    private fun requestBluetoothPermissions() {
        ActivityCompat.requestPermissions(this, requiredPermissions(), 1001)
    }

    private fun requiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

}

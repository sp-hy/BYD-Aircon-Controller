package com.sphy.airconcontroller

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.sphy.airconcontroller.adb.AdbPermissionManager
import com.sphy.airconcontroller.ble.BleConnectionRegistry
import com.sphy.airconcontroller.ble.BleConnectionState
import com.sphy.airconcontroller.ble.toDisplayString
import com.sphy.airconcontroller.byd.BydAcController
import com.sphy.airconcontroller.service.AirconForegroundService
import com.sphy.airconcontroller.storage.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private lateinit var settings: AppSettings
    private lateinit var ac: BydAcController
    private lateinit var setupStatusText: TextView
    private lateinit var acStatusText: TextView
    private lateinit var lastResultText: TextView
    private lateinit var bleStatusText: TextView
    private lateinit var dumpText: TextView

    private val bleStatusListener: (BleConnectionState) -> Unit = { state ->
        bleStatusText.text = state.toDisplayString(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        settings = AppSettings(this)
        ac = BydAcController(this)

        setupStatusText = findViewById(R.id.setupStatusText)
        acStatusText = findViewById(R.id.acStatusText)
        lastResultText = findViewById(R.id.lastResultText)
        bleStatusText = findViewById(R.id.bleStatusText)
        dumpText = findViewById(R.id.dumpText)

        val deviceNameInput = findViewById<TextInputEditText>(R.id.deviceNameInput)
        deviceNameInput.setText(settings.espDeviceName)

        findViewById<Button>(R.id.authorizeButton).setOnClickListener {
            if (!AdbPermissionManager.isPortOpen()) {
                runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }
            }
            lifecycleScope.launch { AdbPermissionManager.runSetup(this@MainActivity) }
        }
        findViewById<Button>(R.id.refreshButton).setOnClickListener { refreshStatus() }
        findViewById<Button>(R.id.acOnButton).setOnClickListener { runAc("Climate ON") { ac.start() } }
        findViewById<Button>(R.id.acOffButton).setOnClickListener { runAc("Climate OFF") { ac.stop() } }
        findViewById<Button>(R.id.tempDownButton).setOnClickListener { runAc("Temp −") { ac.nudgeDriverTemp(-1) } }
        findViewById<Button>(R.id.tempUpButton).setOnClickListener { runAc("Temp +") { ac.nudgeDriverTemp(1) } }
        findViewById<Button>(R.id.fanDownButton).setOnClickListener { runAc("Fan −") { ac.nudgeFan(-1) } }
        findViewById<Button>(R.id.fanUpButton).setOnClickListener { runAc("Fan +") { ac.nudgeFan(1) } }
        findViewById<Button>(R.id.autoButton).setOnClickListener { runAc("Auto") { ac.setAuto(true) } }
        findViewById<Button>(R.id.recircButton).setOnClickListener { runAc("Recirc toggle") { ac.toggleRecirc() } }
        findViewById<Button>(R.id.maxCoolButton).setOnClickListener { runAc("Max cool") { ac.setMaxCool(true) } }
        findViewById<Button>(R.id.dumpMethodsButton).setOnClickListener {
            lifecycleScope.launch {
                val dump = withContext(Dispatchers.IO) { ac.dumpMethods() }
                dumpText.text = dump
            }
        }

        findViewById<Button>(R.id.startServiceButton).setOnClickListener {
            settings.espDeviceName = deviceNameInput.text?.toString()?.trim().orEmpty()
                .ifBlank { AppSettings.DEFAULT_DEVICE_NAME }
            if (!hasBluetoothPermissions()) {
                requestBluetoothPermissions()
                Snackbar.make(it, R.string.missing_permissions, Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            ContextCompat.startForegroundService(this, Intent(this, AirconForegroundService::class.java))
            Snackbar.make(it, R.string.service_running, Snackbar.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.stopServiceButton).setOnClickListener {
            stopService(Intent(this, AirconForegroundService::class.java))
            Snackbar.make(it, R.string.service_stopped, Snackbar.LENGTH_SHORT).show()
        }

        observeAdbState()
        maybePromptHiddenApiConsent()
        startAdbSetupIfNeeded()
        refreshStatus()
    }

    override fun onStart() {
        super.onStart()
        BleConnectionRegistry.addListener(bleStatusListener)
        refreshStatus()
    }

    override fun onStop() {
        BleConnectionRegistry.removeListener(bleStatusListener)
        super.onStop()
    }

    private fun observeAdbState() {
        lifecycleScope.launch {
            AdbPermissionManager.state.collect { state ->
                setupStatusText.text = when (state) {
                    is AdbPermissionManager.SetupState.Idle ->
                        if (AdbPermissionManager.isSetupComplete(this@MainActivity)) {
                            getString(R.string.setup_ready)
                        } else {
                            getString(R.string.setup_needed)
                        }
                    is AdbPermissionManager.SetupState.Connecting -> getString(R.string.setup_connecting)
                    is AdbPermissionManager.SetupState.WaitingAuth -> getString(R.string.setup_waiting_auth)
                    is AdbPermissionManager.SetupState.Granting -> getString(R.string.setup_granting)
                    is AdbPermissionManager.SetupState.Done -> getString(R.string.setup_ready)
                    is AdbPermissionManager.SetupState.Failed -> getString(R.string.setup_failed, state.reason)
                }
            }
        }
    }

    private fun startAdbSetupIfNeeded() {
        lifecycleScope.launch {
            AdbPermissionManager.ensureVehicleApiAccess(this@MainActivity)
            if (!AdbPermissionManager.isSetupComplete(this@MainActivity)) {
                AdbPermissionManager.runSetup(this@MainActivity)
            }
            refreshStatus()
        }
    }

    private fun maybePromptHiddenApiConsent() {
        if (AdbPermissionManager.hasHiddenApiConsent(this) ||
            AdbPermissionManager.hasBeenPromptedForHiddenApi(this)
        ) {
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.hidden_api_title)
            .setMessage(R.string.hidden_api_body)
            .setCancelable(false)
            .setPositiveButton(R.string.hidden_api_allow) { _, _ ->
                AdbPermissionManager.setHiddenApiConsent(this, true)
                AdbPermissionManager.markHiddenApiPrompted(this)
                lifecycleScope.launch {
                    val applied = AdbPermissionManager.ensureVehicleApiAccess(this@MainActivity)
                    if (applied) AdbPermissionManager.restartApp(this@MainActivity)
                }
            }
            .setNegativeButton(R.string.hidden_api_not_now) { _, _ ->
                AdbPermissionManager.markHiddenApiPrompted(this)
            }
            .show()
    }

    private fun runAc(label: String, action: () -> BydAcController.CommandResult) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { action() }
            lastResultText.text = getString(
                R.string.last_result_fmt,
                label,
                result.method,
                result.detail
            )
            val snack = if (result.success) {
                getString(R.string.command_ok, label)
            } else {
                getString(R.string.command_fail, label, result.detail)
            }
            Snackbar.make(lastResultText, snack, Snackbar.LENGTH_LONG).show()
            refreshStatus()
        }
    }

    private fun refreshStatus() {
        lifecycleScope.launch {
            val snap = withContext(Dispatchers.IO) { ac.snapshot() }
            acStatusText.text = snap.toDisplayString()
            if (AdbPermissionManager.state.value is AdbPermissionManager.SetupState.Idle) {
                setupStatusText.text = if (AdbPermissionManager.isSetupComplete(this@MainActivity)) {
                    getString(R.string.setup_ready)
                } else {
                    getString(R.string.setup_needed)
                }
            }
        }
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

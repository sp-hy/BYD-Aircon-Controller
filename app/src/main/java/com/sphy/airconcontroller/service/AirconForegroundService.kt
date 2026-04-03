package com.sphy.airconcontroller.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.sphy.airconcontroller.R
import com.sphy.airconcontroller.byd.BydApiClient
import com.sphy.airconcontroller.byd.BydConfig
import com.sphy.airconcontroller.byd.SeatLevel
import com.sphy.airconcontroller.byd.SeatMode
import com.sphy.airconcontroller.byd.SeatPosition
import com.sphy.airconcontroller.ble.BleCommandListener
import com.sphy.airconcontroller.ble.BleConnectionRegistry
import com.sphy.airconcontroller.ble.BleConnectionState
import com.sphy.airconcontroller.storage.CredentialsStore
import kotlin.concurrent.thread

class AirconForegroundService : Service() {
    private var bleListener: BleCommandListener? = null
    private lateinit var bydApiClient: BydApiClient
    private var lastRequestedTemp: Int? = null
    private var lastRequestedAtMs: Long = 0L
    private var driverHeatLevel: SeatLevel = SeatLevel.OFF
    private var driverCoolLevel: SeatLevel = SeatLevel.OFF
    private var passengerHeatLevel: SeatLevel = SeatLevel.OFF
    private var passengerCoolLevel: SeatLevel = SeatLevel.OFF

    override fun onCreate() {
        super.onCreate()
        bydApiClient = BydApiClient(this)
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        bleListener?.stop()
        val creds = CredentialsStore(this).load()
        bleListener = BleCommandListener(
            context = this,
            targetName = creds.espDeviceName,
            onCommand = ::handleBleCommand,
            onConnectionState = BleConnectionRegistry::setState
        ).also { it.start() }
        return START_STICKY
    }

    private fun handleBleCommand(command: Byte) {
        val b = command.toInt() and 0xff
        when {
            // Temperature setpoint from pot: 17 = \"Low\" (max cool), 33 = \"High\" (max heat).
            b in 17..33 -> handleTemperatureSetpoint(b)
            b in 0x01..0x04 -> {
                handleSeatButton(b)
            }
            else -> {
                Log.i(
                    "AirconBLE",
                    "ESP32 unknown notify byte=0x${b.toString(16).padStart(2, '0')}"
                )
            }
        }
    }

    private fun handleSeatButton(buttonCode: Int) {
        // Mapping:
        // D4 → passenger seat cooling
        // D3 → passenger seat heating
        // D2 → driver seat cooling
        // D1 → driver seat heating
        val (position, mode) = when (buttonCode) {
            0x01 -> SeatPosition.DRIVER to SeatMode.HEAT
            0x02 -> SeatPosition.DRIVER to SeatMode.COOL
            0x03 -> SeatPosition.PASSENGER to SeatMode.HEAT
            0x04 -> SeatPosition.PASSENGER to SeatMode.COOL
            else -> return
        }
        Log.i(
            "AirconBLE",
            "ESP32 seat button: seat=${position.name.lowercase()} mode=${mode.name.lowercase()} code=0x${buttonCode.toString(16).padStart(2, '0')}"
        )

        // Cycle OFF → LOW → HIGH → OFF for the selected seat/mode, mirroring pyBYD's three levels.
        thread(name = "byd-seat-from-ble") {
            val creds = CredentialsStore(this).load()
            val config = BydConfig(
                username = creds.email,
                password = creds.password,
                controlPin = creds.controlPin,
                countryCode = creds.countryCode,
                baseUrl = creds.baseUrl
            )

            val (levelBefore, levelAfter) = when (position to mode) {
                SeatPosition.DRIVER to SeatMode.HEAT -> {
                    val before = driverHeatLevel
                    val after = driverHeatLevel.next()
                    driverHeatLevel = after
                    before to after
                }
                SeatPosition.DRIVER to SeatMode.COOL -> {
                    val before = driverCoolLevel
                    val after = driverCoolLevel.next()
                    driverCoolLevel = after
                    before to after
                }
                SeatPosition.PASSENGER to SeatMode.HEAT -> {
                    val before = passengerHeatLevel
                    val after = passengerHeatLevel.next()
                    passengerHeatLevel = after
                    before to after
                }
                SeatPosition.PASSENGER to SeatMode.COOL -> {
                    val before = passengerCoolLevel
                    val after = passengerCoolLevel.next()
                    passengerCoolLevel = after
                    before to after
                }
                else -> SeatLevel.OFF to SeatLevel.OFF
            }

            val result = runCatching {
                bydApiClient.login(config)
                val vin = bydApiClient.fetchVehicleList(config)
                    .firstOrNull()
                    ?.vin
                    ?.takeIf { it.isNotBlank() }
                    ?: error("No VIN from fetchVehicleList")

                if (!bydApiClient.verifyControlPassword(config, vin)) {
                    error("Control PIN verification failed")
                }

                bydApiClient.setSeatClimate(
                    config = config,
                    vin = vin,
                    position = position,
                    mode = mode,
                    level = levelAfter
                )
            }

            result.onSuccess { cmd ->
                if (cmd.success) {
                    Log.i(
                        "AirconBLE",
                        "BYD seat command from ESP32 succeeded: seat=${position.name.lowercase()} mode=${mode.name.lowercase()} level=${levelAfter.name.lowercase()} (was ${levelBefore.name.lowercase()}) controlState=${cmd.controlState}"
                    )
                } else {
                    Log.w(
                        "AirconBLE",
                        "BYD seat command from ESP32 returned unsuccessful result: seat=${position.name.lowercase()} mode=${mode.name.lowercase()} controlState=${cmd.controlState}"
                    )
                }
            }.onFailure { t ->
                Log.e(
                    "AirconBLE",
                    "BYD seat command from ESP32 failed for seat=${position.name.lowercase()} mode=${mode.name.lowercase()}: ${t.message}",
                    t
                )
            }
        }
    }

    private fun handleTemperatureSetpoint(tempC: Int) {
        val nowMs = System.currentTimeMillis()
        val elapsed = nowMs - lastRequestedAtMs

        // Debounce while the dial is being turned:
        // - ignore repeats of the same temp within 1s
        // - ignore new temps while we're still within 500ms of the last request
        if (lastRequestedTemp == tempC && elapsed < 1_000L) return
        if (lastRequestedTemp != null && lastRequestedTemp != tempC && elapsed < 500L) return
        lastRequestedTemp = tempC
        lastRequestedAtMs = nowMs

        // BYD's API scale is 15–31°C, but the car UI shows 17–33 (\"Low\"..\"High\").
        // Treat the BLE value as the UI value and subtract 2°C for the actual API request.
        val apiTempC = (tempC - 2).coerceIn(15, 31)

        Log.i(
            "AirconBLE",
            "Requested climate setpoint from ESP32: dial=$tempC°C api=$apiTempC°C"
        )

        val creds = CredentialsStore(this).load()
        val config = BydConfig(
            username = creds.email,
            password = creds.password,
            controlPin = creds.controlPin,
            countryCode = creds.countryCode,
            baseUrl = creds.baseUrl
        )

        thread(name = "byd-climate-from-ble") {
            val result = runCatching {
                // Login + VIN + PIN verify + startClimate, mirroring MainActivity's test button flow.
                bydApiClient.login(config)
                val vin = bydApiClient.fetchVehicleList(config)
                    .firstOrNull()
                    ?.vin
                    ?.takeIf { it.isNotBlank() }
                    ?: error("No VIN from fetchVehicleList")

                if (!bydApiClient.verifyControlPassword(config, vin)) {
                    error("Control PIN verification failed")
                }

                bydApiClient.startClimate(config, vin, temperatureC = apiTempC)
            }

            result.onSuccess { cmd ->
                if (cmd.success) {
                    Log.i(
                        "AirconBLE",
                        "BYD climate command from ESP32 succeeded: dial=$tempC°C api=$apiTempC°C controlState=${cmd.controlState}"
                    )
                } else {
                    Log.w(
                        "AirconBLE",
                        "BYD climate command from ESP32 returned unsuccessful result: dial=$tempC°C api=$apiTempC°C controlState=${cmd.controlState}"
                    )
                }
            }.onFailure { t ->
                Log.e(
                    "AirconBLE",
                    "BYD climate command from ESP32 failed for dial=$tempC°C api=$apiTempC°C: ${t.message}",
                    t
                )
            }
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(getString(R.string.service_notification_body))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.service_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        bleListener?.stop()
        bleListener = null
        BleConnectionRegistry.setState(BleConnectionState.IDLE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "byd-aircon-listener"
        private const val NOTIFICATION_ID = 4012
    }
}

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
import com.sphy.airconcontroller.ble.BleCommandListener
import com.sphy.airconcontroller.ble.BleConnectionRegistry
import com.sphy.airconcontroller.ble.BleConnectionState
import com.sphy.airconcontroller.storage.CredentialsStore

class AirconForegroundService : Service() {
    private var bleListener: BleCommandListener? = null

    override fun onCreate() {
        super.onCreate()
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
        // Remote climate is still disabled; log so you can verify the path (e.g. adb logcat -s AirconBLE:I).
        // Byte = pin number: 0x00=D0 … 0x03=D3 (matches firmware).
        val b = command.toInt() and 0xff
        val pin = if (b in 0..3) "D$b" else "?"
        Log.i(
            "AirconBLE",
            "ESP32 button/notify $pin byte=0x${b.toString(16).padStart(2, '0')}"
        )
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

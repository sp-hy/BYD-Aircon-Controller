package com.sphy.airconcontroller.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

class BleCommandListener(
    private val context: Context,
    private val targetName: String,
    private val onCommand: (Byte) -> Unit,
    private val onConnectionState: (BleConnectionState) -> Unit = {}
) {
    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
    private val adapter: BluetoothAdapter? get() = bluetoothManager?.adapter
    private var gatt: BluetoothGatt? = null
    @Volatile
    private var stopped = false

    private val serviceUuid = UUID.fromString("0f1d2a40-2f5f-4a4d-b3c1-91f7b799f0a1")
    private val commandUuid = UUID.fromString("8388fdd2-cd4e-4f6d-a32f-03c2f0bc62a5")
    private val cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    /** Raw AD payload may list our UUID while [ScanRecord.getServiceUuids] stays empty on some OEMs. */
    private val serviceUuidAdPatterns: List<ByteArray> by lazy {
        val u = serviceUuid
        val msb = u.mostSignificantBits
        val lsb = u.leastSignificantBits
        val be = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN).apply {
            putLong(msb)
            putLong(lsb)
        }.array()
        val le = ByteArray(16)
        for (i in 0 until 8) le[i] = ((lsb ushr (8 * i)) and 0xFF).toByte()
        for (i in 8 until 16) le[i] = ((msb ushr (8 * (i - 8))) and 0xFF).toByte()
        listOf(be, be.reversedArray(), le, le.reversedArray())
    }

    /** Prefer scan record: [BluetoothDevice.getName] is often null during scans on Android 10+. */
    private fun resolvedDeviceName(result: ScanResult): String? =
        result.scanRecord?.deviceName ?: result.device.name

    /** True if the scan record lists our GATT service (adv or scan response). */
    private fun advertisesOurService(result: ScanResult): Boolean =
        result.scanRecord?.serviceUuids?.any { it.uuid == serviceUuid } == true

    private fun rawAdvContainsOurServiceUuid(result: ScanResult): Boolean {
        val raw = result.scanRecord?.bytes ?: return false
        for (pattern in serviceUuidAdPatterns) {
            if (containsSubArray(raw, pattern)) return true
        }
        return false
    }

    private fun containsSubArray(haystack: ByteArray, needle: ByteArray): Boolean {
        if (needle.isEmpty() || needle.size > haystack.size) return false
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return true
        }
        return false
    }

    private fun scanImpliesOurPeripheral(result: ScanResult): Boolean =
        advertisesOurService(result) || rawAdvContainsOurServiceUuid(result)

    /**
     * Identify our peripheral: UUID from OS, raw AD bytes, or advertised local name.
     * When UUID matches, require name prefix only if the OS gave us a name (avoid blocking on null name).
     */
    private fun isTargetDevice(result: ScanResult): Boolean {
        val prefix = targetName.trim().ifEmpty { "BYD-Aircon" }
        val name = resolvedDeviceName(result)
        val uuidMatch = scanImpliesOurPeripheral(result)
        val nameMatch = name != null && name.startsWith(prefix, ignoreCase = true)
        if (uuidMatch) {
            if (name == null) return true
            return name.startsWith(prefix, ignoreCase = true)
        }
        return nameMatch
    }

    /**
     * If the user paired the ESP32 in system settings, [BluetoothDevice.getName] is often populated
     * here even when passive scans omit the name — connect without relying on scan callbacks.
     */
    @SuppressLint("MissingPermission")
    private fun tryConnectBondedDevice(): Boolean {
        val bonded = adapter?.bondedDevices ?: return false
        val prefix = targetName.trim().ifEmpty { "BYD-Aircon" }
        for (device in bonded) {
            val name = device.name ?: continue
            if (!name.startsWith(prefix, ignoreCase = true)) continue
            adapter?.bluetoothLeScanner?.stopScan(scanCallback)
            onConnectionState(BleConnectionState.CONNECTING)
            connect(device)
            return true
        }
        return false
    }

    private val scanSettings: ScanSettings
        get() = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handleScanResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            for (result in results) handleScanResult(result)
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            if (!stopped) onConnectionState(BleConnectionState.DISCONNECTED)
        }
    }

    private fun handleScanResult(result: ScanResult) {
        if (!isTargetDevice(result)) return
        adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        onConnectionState(BleConnectionState.CONNECTING)
        connect(result.device)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (stopped) return
            when (newState) {
                BluetoothProfile.STATE_CONNECTED ->
                    onConnectionState(BleConnectionState.CONNECTED)
                BluetoothProfile.STATE_DISCONNECTED ->
                    onConnectionState(BleConnectionState.DISCONNECTED)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val service: BluetoothGattService = gatt.getService(serviceUuid) ?: return
            val characteristic = service.getCharacteristic(commandUuid) ?: return
            gatt.setCharacteristicNotification(characteristic, true)
            val descriptor = characteristic.getDescriptor(cccdUuid) ?: return
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                characteristic.value
            } else {
                @Suppress("DEPRECATION")
                characteristic.value
            }
            val first = value?.firstOrNull() ?: return
            onCommand(first)
        }
    }

    fun start() {
        if (!hasPermission()) {
            onConnectionState(BleConnectionState.IDLE)
            return
        }
        val scanner = adapter?.bluetoothLeScanner ?: run {
            onConnectionState(BleConnectionState.IDLE)
            return
        }
        stopped = false
        if (tryConnectBondedDevice()) return
        onConnectionState(BleConnectionState.SCANNING)
        scanner.startScan(null, scanSettings, scanCallback)
    }

    fun stop() {
        stopped = true
        adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        gatt?.close()
        gatt = null
        onConnectionState(BleConnectionState.IDLE)
    }

    @SuppressLint("MissingPermission")
    private fun connect(device: BluetoothDevice) {
        if (!hasPermission()) return
        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            @Suppress("DEPRECATION")
            device.connectGatt(context, false, gattCallback)
        }
    }

    private fun hasPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}

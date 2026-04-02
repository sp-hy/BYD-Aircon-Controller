package com.sphy.airconcontroller.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.util.UUID

class BleCommandListener(
    private val context: Context,
    private val targetName: String,
    private val onCommand: (Byte) -> Unit
) {
    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
    private val adapter: BluetoothAdapter? get() = bluetoothManager?.adapter
    private var gatt: BluetoothGatt? = null

    private val serviceUuid = UUID.fromString("0f1d2a40-2f5f-4a4d-b3c1-91f7b799f0a1")
    private val commandUuid = UUID.fromString("8388fdd2-cd4e-4f6d-a32f-03c2f0bc62a5")
    private val cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val deviceName = result.device.name ?: return
            if (deviceName.startsWith(targetName, ignoreCase = true)) {
                adapter?.bluetoothLeScanner?.stopScan(this)
                connect(result.device)
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
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
        if (!hasPermission()) return
        adapter?.bluetoothLeScanner?.startScan(scanCallback)
    }

    fun stop() {
        adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        gatt?.close()
        gatt = null
    }

    private fun connect(device: BluetoothDevice) {
        if (!hasPermission()) return
        gatt = device.connectGatt(context, false, gattCallback)
    }

    private fun hasPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}

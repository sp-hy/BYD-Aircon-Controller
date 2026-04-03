package com.sphy.airconcontroller.ble

import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArrayList

object BleConnectionRegistry {
    @Volatile
    private var state: BleConnectionState = BleConnectionState.IDLE

    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArrayList<(BleConnectionState) -> Unit>()

    fun getState(): BleConnectionState = state

    fun setState(newState: BleConnectionState) {
        state = newState
        mainHandler.post {
            listeners.forEach { it(newState) }
        }
    }

    fun addListener(listener: (BleConnectionState) -> Unit) {
        listeners.add(listener)
        mainHandler.post { listener(state) }
    }

    fun removeListener(listener: (BleConnectionState) -> Unit) {
        listeners.remove(listener)
    }
}

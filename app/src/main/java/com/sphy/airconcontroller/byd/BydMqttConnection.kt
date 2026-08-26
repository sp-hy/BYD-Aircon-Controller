package com.sphy.airconcontroller.byd

import android.util.Log
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Last decrypted MQTT `vehicleInfo` payload (`data.respondData`) per VIN — pyBYD
 * [VehicleRealtimeData] used with HVAC in [SeatClimateParams.from_current_state].
 */
object BydRealtimeStore {
    private val vehicleInfoByVin = ConcurrentHashMap<String, JSONObject>()

    fun putVehicleInfo(vin: String, respondData: JSONObject) {
        vehicleInfoByVin[vin] = JSONObject(respondData.toString())
    }

    fun snapshotForVin(vin: String): JSONObject? {
        val v = vehicleInfoByVin[vin] ?: return null
        return JSONObject(v.toString())
    }

    fun clear() {
        vehicleInfoByVin.clear()
    }
}

/**
 * BYD EMQ MQTT (pyBYD [_mqtt]: bootstrap [/app/emqAuth/getEmqBrokerIp], TLS, topic `oversea/res/{userId}`).
 */
internal class BydMqttConnection(
    private val session: BydSession,
    private val decryptKeyHex: String,
    private val brokerHost: String,
    private val brokerPort: Int,
    private val clientId: String,
    private val mqttPassword: String,
) : MqttCallback {

    private var client: MqttClient? = null

    fun start() {
        val serverUri = "ssl://$brokerHost:$brokerPort"
        val c = MqttClient(serverUri, clientId, MemoryPersistence())
        c.setCallback(this)
        val opts = MqttConnectOptions().apply {
            userName = session.userId
            password = mqttPassword.toCharArray()
            isAutomaticReconnect = true
            isCleanSession = true
        }
        c.connect(opts)
        val topic = "oversea/res/${session.userId}"
        c.subscribe(topic, 0)
        client = c
        Log.i("BydMqtt", "connected host=$brokerHost port=$brokerPort topic=$topic clientId=$clientId")
    }

    fun stop() {
        try {
            client?.setCallback(null)
            client?.disconnect()
        } catch (_: Exception) {
        }
        client = null
    }

    override fun connectionLost(cause: Throwable?) {
        Log.w("BydMqtt", "connection lost: ${cause?.message}")
    }

    override fun messageArrived(topic: String?, message: MqttMessage?) {
        if (message == null) return
        try {
            val parsed = decodeMqttPayload(message.payload, decryptKeyHex)
            val event = parsed.optString("event", "")
            val vin = parsed.optString("vin", "").takeIf { it.isNotBlank() } ?: return
            val data = parsed.optJSONObject("data")
            val respondData: JSONObject? = when {
                data != null && data.has("respondData") -> data.optJSONObject("respondData")
                data != null -> data
                else -> parsed
            }
            if (event == "vehicleInfo" && respondData != null) {
                BydRealtimeStore.putVehicleInfo(vin, respondData)
                Log.i("BydMqtt", "vehicleInfo vin=$vin keys=${respondData.length()}")
            }
        } catch (e: Exception) {
            Log.w("BydMqtt", "message failed: ${e.message}")
        }
    }

    override fun deliveryComplete(token: IMqttDeliveryToken?) {}

    companion object {

        fun decodeMqttPayload(payload: ByteArray, decryptKeyHex: String): JSONObject {
            var rawText = payload.toString(Charsets.US_ASCII)
            rawText = rawText.filterNot { it.isWhitespace() }
            val plain = CryptoUtils.aesDecryptUtf8(rawText, decryptKeyHex)
            return JSONObject(plain.trim())
        }

        fun parseBrokerHostPort(broker: String): Pair<String, Int> {
            var v = broker.trim()
            if (v.contains("://")) v = v.substringAfter("://")
            if (v.contains("/")) v = v.substringBefore("/")
            val parts = v.split(":")
            return if (parts.size >= 2 && parts[1].all { it.isDigit() }) {
                parts[0] to parts[1].toInt()
            } else {
                v to 8883
            }
        }

        fun buildClientId(): String {
            val imeiMd5 = "00000000000000000000000000000000".uppercase()
            return if (imeiMd5.any { it != '0' }) {
                "oversea_$imeiMd5"
            } else {
                "oversea_${CryptoUtils.md5Hex("BANGCLE01234")}"
            }
        }

        fun buildMqttPassword(signToken: String, clientId: String, userId: String, tsSeconds: Long): String {
            val tsText = tsSeconds.toString()
            val base = "$signToken$clientId$userId$tsText"
            return "${tsText}${CryptoUtils.md5Hex(base)}"
        }
    }
}

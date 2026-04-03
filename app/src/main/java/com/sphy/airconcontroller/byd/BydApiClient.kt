package com.sphy.airconcontroller.byd

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.JavaNetCookieJar
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.net.CookieManager
import java.net.CookiePolicy

class BydApiClient(
    private val codec: EnvelopeCodec,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(JavaNetCookieJar(CookieManager().apply { setCookiePolicy(CookiePolicy.ACCEPT_ALL) }))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
) {
    constructor(
        context: Context,
        httpClient: OkHttpClient = OkHttpClient.Builder()
            .cookieJar(JavaNetCookieJar(CookieManager().apply { setCookiePolicy(CookiePolicy.ACCEPT_ALL) }))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    ) : this(BangcleCodec(context), httpClient)

    private var session: BydSession? = null

    fun ensureCodecReady(): Boolean = codec.isTableLoaded()

    fun login(config: BydConfig): BydSession {
        val response = postSecure("${config.baseUrl}/app/account/login", buildLoginOuter(config))
        val code = response.optString("code", "")
        if (code.isBlank()) {
            throw IllegalStateException("Login failed: missing response code")
        }
        if (code != "0") {
            val message = response.optString("message", "login_failed")
            throw IllegalStateException("Login failed: code=$code message=$message")
        }

        val respondData = response.optString("respondData", "")
        if (respondData.isBlank()) {
            throw IllegalStateException("Login failed: missing respondData")
        }
        val loginPlain = CryptoUtils.aesDecryptUtf8(respondData, CryptoUtils.pwdLoginKey(config.password))
        val token = JSONObject(loginPlain).optJSONObject("token")
            ?: throw IllegalStateException("Login failed: missing token object")
        val userId = token.optString("userId", "")
        val signToken = token.optString("signToken", "")
        val encryToken = token.optString("encryToken", token.optString("encryptToken", ""))
        if (userId.isBlank() || signToken.isBlank() || encryToken.isBlank()) {
            throw IllegalStateException("Login failed: missing token fields")
        }
        return BydSession(userId, signToken, encryToken).also { session = it }
    }

    private fun buildLoginOuter(config: BydConfig): JSONObject {
        val nowMs = System.currentTimeMillis()
        val reqTimestamp = nowMs.toString()
        val inner = JSONObject()
            .put("appInnerVersion", "323")
            .put("appVersion", "3.2.3")
            .put("deviceName", "XIAOMIPOCO F1")
            .put("deviceType", "0")
            .put("imeiMD5", "00000000000000000000000000000000")
            .put("isAuto", "1")
            .put("mobileBrand", "XIAOMI")
            .put("mobileModel", "POCO F1")
            .put("networkType", "wifi")
            .put("osType", "15")
            .put("osVersion", "35")
            .put("random", UUID.randomUUID().toString().replace("-", "").uppercase())
            .put("softType", "0")
            .put("timeStamp", reqTimestamp)
            .put("timeZone", ZoneId.systemDefault().id.ifBlank { "Australia/Sydney" })

        val encryData = CryptoUtils.aesEncryptHex(inner.toString(), CryptoUtils.pwdLoginKey(config.password))
        val signFields = mutableMapOf<String, String?>()
        val it = inner.keys()
        while (it.hasNext()) {
            val k = it.next()
            signFields[k] = inner.opt(k)?.toString()
        }
        signFields["countryCode"] = config.countryCode.uppercase()
        signFields["functionType"] = "pwdLogin"
        signFields["identifier"] = config.username
        signFields["identifierType"] = "0"
        signFields["language"] = "en"
        signFields["reqTimestamp"] = reqTimestamp
        val sign = CryptoUtils.sha1Mixed(
            CryptoUtils.buildSignString(signFields, CryptoUtils.md5Hex(config.password))
        )

        val outer = JSONObject()
            .put("countryCode", config.countryCode.uppercase())
            .put("encryData", encryData)
            .put("functionType", "pwdLogin")
            .put("identifier", config.username)
            .put("identifierType", "0")
            .put("imeiMD5", "00000000000000000000000000000000")
            .put("isAuto", "1")
            .put("language", "en")
            .put("reqTimestamp", reqTimestamp)
            .put("sign", sign)
            .put("signKey", config.password)
            .put("ostype", "and")
            .put("imei", "BANGCLE01234")
            .put("mac", "00:00:00:00:00:00")
            .put("model", "POCO F1")
            .put("sdk", "35")
            .put("mod", "Xiaomi")
            .put("serviceTime", System.currentTimeMillis().toString())
        outer.put("checkcode", CryptoUtils.computeCheckcode(outer))
        return outer
    }

    fun fetchVehicleList(config: BydConfig): List<VehicleSummary> {
        requireNotNull(session) { "Call login() first." }
        val response = postTokenJson(config, "/app/account/getAllListByUserId", buildInnerBase())
        val items = when (response) {
            is JSONArray -> response
            is JSONObject -> response.optJSONArray("diLinkAutoInfoList") ?: JSONArray()
            else -> JSONArray()
        }
        return buildList {
            for (i in 0 until items.length()) {
                val obj = items.optJSONObject(i) ?: continue
                add(VehicleSummary(obj.optString("vin"), obj.optString("vehicleName")))
            }
        }
    }

    fun fetchCapabilityFunctionNos(config: BydConfig, vin: String): Set<String> {
        val response = postTokenJson(config, "/app/config/getAllBrandCommonConfig", buildInnerBase().put("vin", vin))
        val obj = response as? JSONObject ?: return emptySet()
        val functions = obj.optJSONArray("functionList") ?: return emptySet()
        return buildSet {
            for (i in 0 until functions.length()) {
                val item = functions.optJSONObject(i) ?: continue
                val functionNo = item.optString("functionNo")
                if (functionNo.isNotBlank()) add(functionNo)
            }
        }
    }

    fun isClimateOn(config: BydConfig, vin: String): Boolean {
        val response = postTokenJson(config, "/control/getStatusNow", buildInnerBase().put("vin", vin))
        val obj = response as? JSONObject ?: return false
        val status = obj.optInt("status", -1)
        return status == 1
    }

    /**
     * Raw JSON from `/control/getStatusNow` (decrypted). Used to build seat commands that mirror
     * pyBYD [SeatClimateParams.from_current_state].
     */
    fun fetchHvacStatusNow(config: BydConfig, vin: String): JSONObject {
        val response = postTokenJson(config, "/control/getStatusNow", buildInnerBase().put("vin", vin))
        return when (response) {
            is JSONObject -> response
            else -> JSONObject()
        }
    }

    fun verifyControlPassword(config: BydConfig, vin: String): Boolean {
        val inner = buildInnerBase()
            .put("vin", vin)
            .put("commandPwd", CryptoUtils.md5Hex(config.controlPin))
            .put("functionType", "remoteControl")
        val response = postTokenJson(config, "/vehicle/vehicleswitch/verifyControlPassword", inner)
        return response is JSONObject || response is JSONArray
    }

    fun startClimate(config: BydConfig, vin: String, temperatureC: Int = 21, durationMinutes: Int = 20): CommandResult {
        val controlMap = JSONObject()
            .put("mainSettingTemp", (temperatureC - 14).coerceIn(1, 17))
            .put("copilotSettingTemp", (temperatureC - 14).coerceIn(1, 17))
            .put("timeSpan", when (durationMinutes) {
                10 -> 1
                15 -> 2
                20 -> 3
                25 -> 4
                else -> 5
            })
            .put("remoteMode", 4)
            .put("cycleMode", 2)
            .put("airAccuracy", 1)
            .put("airConditioningMode", 1)
        return remoteControl(config, vin, ClimateCommandType.START, controlMap)
    }

    fun stopClimate(config: BydConfig, vin: String): CommandResult {
        return remoteControl(config, vin, ClimateCommandType.STOP, null)
    }

    fun toggleClimate(config: BydConfig, vin: String, currentlyOn: Boolean): CommandResult {
        return if (currentlyOn) stopClimate(config, vin) else startClimate(config, vin)
    }

    fun setSeatClimate(
        config: BydConfig,
        vin: String,
        position: SeatPosition,
        mode: SeatMode,
        level: SeatLevel
    ): CommandResult {
        val requestSerial = UUID.randomUUID().toString()
        val payload = buildInnerBase()
            .put("vin", vin)
            .put("commandType", "VENTILATIONHEATING")
            .put("commandPwd", CryptoUtils.md5Hex(config.controlPin))
            .put("requestSerial", requestSerial)

        // BYD expects every seat field on each command. Merge with live /control/getStatusNow like pyBYD
        // SeatClimateParams.from_current_state(), then apply one change (camelCase keys per pyBYD).
        val statusRoot = runCatching { fetchHvacStatusNow(config, vin) }.getOrNull()
        val statusSlice = statusRoot?.let { SeatClimateParams.effectiveStatusSlice(it) }
        val base = when {
            statusSlice == null || statusSlice.length() == 0 -> SeatClimateParams.fallbackDefaults()
            !SeatClimateParams.sliceContainsSeatHints(statusSlice) -> SeatClimateParams.fallbackDefaults()
            else -> SeatClimateParams.fromHvacStatusJson(statusSlice)
        }

        val controlParams = JSONObject(base.toString()).apply {
            when (position) {
                SeatPosition.DRIVER -> when (mode) {
                    SeatMode.HEAT -> put("mainHeat", level.commandValue)
                    SeatMode.COOL -> put("mainVentilation", level.commandValue)
                }
                SeatPosition.PASSENGER -> when (mode) {
                    SeatMode.HEAT -> put("copilotHeat", level.commandValue)
                    SeatMode.COOL -> put("copilotVentilation", level.commandValue)
                }
            }
            SeatClimateParams.applyHeatVentMutualExclusion(this, position, mode, level)
            SeatClimateParams.normalizeFrontSeatHeatNotApplicable(this)
            put("chairType", position.chairType)
            put("remoteMode", 1)
        }

        payload.put("controlParamsMap", controlParams.toString())

        runCatching {
            postTokenJson(config, "/control/remoteControl", payload)
        }.onFailure { ex ->
            val msg = ex.message.orEmpty()
            if (!msg.contains("/control/remoteControl failed: code=1001")) throw ex
            // Same intermittent "Service error" as remoteControlResult; command may still apply.
        }
        repeat(10) {
            val poll = buildInnerBase()
                .put("vin", vin)
                .put("commandType", "VENTILATIONHEATING")
                .put("commandPwd", CryptoUtils.md5Hex(config.controlPin))
                .put("requestSerial", requestSerial)
            val result = try {
                postTokenJson(config, "/control/remoteControlResult", poll) as? JSONObject ?: JSONObject()
            } catch (ex: IllegalStateException) {
                if (ex.message?.contains("/control/remoteControlResult failed: code=1001") == true) {
                    return CommandResult(success = true, controlState = 1, requestSerial = requestSerial)
                }
                throw ex
            }
            val res = result.optInt("res", 0)
            val controlState = result.optInt("controlState", 0)
            val done = (res >= 2) || (controlState != 0) || result.has("result")
            if (done) {
                val success = (res == 2) || (controlState == 1)
                val finalState = if (controlState != 0) controlState else if (success) 1 else 2
                return CommandResult(success, finalState, requestSerial)
            }
            Thread.sleep(1500)
        }
        return CommandResult(success = false, controlState = 0, requestSerial = requestSerial)
    }

    private fun remoteControl(
        config: BydConfig,
        vin: String,
        command: ClimateCommandType,
        controlParams: JSONObject?
    ): CommandResult {
        val requestSerial = UUID.randomUUID().toString()
        val payload = buildInnerBase()
            .put("vin", vin)
            .put("commandType", command.commandType)
            .put("commandPwd", CryptoUtils.md5Hex(config.controlPin))
            .put("requestSerial", requestSerial)

        if (controlParams != null) {
            payload.put("controlParamsMap", controlParams.toString())
        }

        postTokenJson(config, "/control/remoteControl", payload)
        repeat(10) {
            val poll = buildInnerBase()
                .put("vin", vin)
                .put("commandType", command.commandType)
                .put("commandPwd", CryptoUtils.md5Hex(config.controlPin))
                .put("requestSerial", requestSerial)
            val result = try {
                postTokenJson(config, "/control/remoteControlResult", poll) as? JSONObject ?: JSONObject()
            } catch (ex: IllegalStateException) {
                // Observed on some accounts/head units: remote command is applied, but result endpoint
                // intermittently returns code=1001 "Service error". Treat this as optimistic success.
                if (ex.message?.contains("/control/remoteControlResult failed: code=1001") == true) {
                    return CommandResult(success = true, controlState = 1, requestSerial = requestSerial)
                }
                throw ex
            }
            val res = result.optInt("res", 0)
            val controlState = result.optInt("controlState", 0)
            val done = (res >= 2) || (controlState != 0) || result.has("result")
            if (done) {
                val success = (res == 2) || (controlState == 1)
                val finalState = if (controlState != 0) controlState else if (success) 1 else 2
                return CommandResult(success, finalState, requestSerial)
            }
            Thread.sleep(1500)
        }
        return CommandResult(success = false, controlState = 0, requestSerial = requestSerial)
    }

    private fun buildInnerBase(
        nowMs: Long = System.currentTimeMillis(),
        vin: String? = null,
        requestSerial: String? = null
    ): JSONObject {
        val inner = JSONObject()
            .put("deviceType", "0")
            .put("imeiMD5", "00000000000000000000000000000000")
            .put("networkType", "wifi")
            .put("random", UUID.randomUUID().toString().replace("-", "").uppercase())
            .put("timeStamp", nowMs.toString())
            .put("version", "323")
        if (!vin.isNullOrBlank()) inner.put("vin", vin)
        if (!requestSerial.isNullOrBlank()) inner.put("requestSerial", requestSerial)
        return inner
    }

    private fun postTokenJson(config: BydConfig, endpoint: String, inner: JSONObject): Any {
        val currentSession = requireNotNull(session) { "Call login() first." }
        val nowMs = System.currentTimeMillis()
        val reqTimestamp = nowMs.toString()
        val contentKey = CryptoUtils.md5Hex(currentSession.encryToken)
        val signKey = CryptoUtils.md5Hex(currentSession.signToken)
        val encryData = CryptoUtils.aesEncryptHex(inner.toString(), contentKey)

        val signFields = mutableMapOf<String, String?>()
        val keys = inner.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            signFields[key] = inner.opt(key)?.toString()
        }
        signFields["countryCode"] = config.countryCode.uppercase()
        signFields["identifier"] = currentSession.userId
        signFields["imeiMD5"] = "00000000000000000000000000000000"
        signFields["language"] = "en"
        signFields["reqTimestamp"] = reqTimestamp

        val sign = CryptoUtils.sha1Mixed(CryptoUtils.buildSignString(signFields, signKey))
        val outer = JSONObject()
            .put("countryCode", config.countryCode.uppercase())
            .put("encryData", encryData)
            .put("identifier", currentSession.userId)
            .put("imeiMD5", "00000000000000000000000000000000")
            .put("language", "en")
            .put("reqTimestamp", reqTimestamp)
            .put("sign", sign)
            .put("ostype", "and")
            .put("imei", "BANGCLE01234")
            .put("mac", "00:00:00:00:00:00")
            .put("model", "POCO F1")
            .put("sdk", "35")
            .put("mod", "Xiaomi")
            .put("serviceTime", nowMs.toString())
        outer.put("checkcode", CryptoUtils.computeCheckcode(outer))

        val response = postSecure("${config.baseUrl}$endpoint", outer)
        val code = response.optString("code", "")
        if (code != "0") {
            val message = response.optString("message", "")
            throw IllegalStateException("$endpoint failed: code=$code message=$message")
        }
        val respondData = response.optString("respondData", "")
        if (respondData.isBlank()) return JSONObject()
        val plain = CryptoUtils.aesDecryptUtf8(respondData, contentKey).trim()
        if (plain.isBlank()) return JSONObject()
        return if (plain.startsWith("[")) JSONArray(plain) else JSONObject(plain)
    }

    private fun postSecure(url: String, outerPayload: JSONObject): JSONObject {
        val mediaType = "application/json; charset=UTF-8".toMediaType()
        val bodyEnvelope = JSONObject().put("request", codec.encodeEnvelope(outerPayload.toString()))
        val request = Request.Builder()
            .url(url)
            .header("accept-encoding", "identity")
            .header("content-type", "application/json; charset=UTF-8")
            .header("user-agent", "okhttp/4.12.0")
            .post(bodyEnvelope.toString().toRequestBody(mediaType))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}: $body")
            }
            val responseJson = JSONObject(body)
            val encoded = responseJson.optString("response")
            if (encoded.isBlank()) return responseJson
            val decoded = codec.decodeEnvelope(encoded)
            return if (decoded.trim().startsWith("{")) JSONObject(decoded) else responseJson
        }
    }
}

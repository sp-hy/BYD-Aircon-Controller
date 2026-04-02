package com.sphy.airconcontroller

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sphy.airconcontroller.byd.BydApiClient
import com.sphy.airconcontroller.byd.BydConfig
import com.sphy.airconcontroller.byd.CryptoUtils
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Base64

@RunWith(AndroidJUnit4::class)
class BydApiClientIntegrationInstrumentedTest {
    @Test
    fun mockFlow_loginVerifyStartClimate_succeeds() {
        val server = MockWebServer()
        val tokenPayload = """{"token":{"userId":"u1","signToken":"s","encryToken":"e"}}"""
        val respondData = CryptoUtils.aesEncryptHex(tokenPayload, CryptoUtils.pwdLoginKey("secret"))
        server.enqueue(okEnvelope("""{"code":"0","message":"SUCCESS","respondData":"$respondData"}"""))
        server.enqueue(okEnvelope("""{"code":"0","success":true}"""))
        server.enqueue(okEnvelope("""{"code":"0","requestSerial":"abc"}"""))
        server.enqueue(okEnvelope("""{"res":2,"controlState":1}"""))
        server.start()

        try {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val client = BydApiClient(context)
            val config = BydConfig(
                username = "user@example.com",
                password = "secret",
                controlPin = "123456",
                countryCode = "AU",
                baseUrl = server.url("/").toString().removeSuffix("/")
            )
            client.login(config)
            assertTrue(client.verifyControlPassword(config, "VIN001"))
            assertTrue(client.startClimate(config, "VIN001").success)
        } finally {
            server.shutdown()
        }
    }

    private fun okEnvelope(payload: String): MockResponse {
        val encoded = Base64.getEncoder().encodeToString(payload.encodeToByteArray())
        return MockResponse().setResponseCode(200).setBody("""{"response":"F$encoded"}""")
    }
}

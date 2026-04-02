package com.sphy.airconcontroller

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sphy.airconcontroller.byd.BangcleCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BangcleCodecInstrumentedTest {
    @Test
    fun codecLoadsAssetAndRoundTrips() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val codec = BangcleCodec(context)
        assertTrue(codec.isTableLoaded())

        val payload = """{"test":"ok","v":1}"""
        val encoded = codec.encodeEnvelope(payload)
        val decoded = codec.decodeEnvelope(encoded)
        assertEquals(payload, decoded)
    }
}

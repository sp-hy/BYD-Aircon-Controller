package com.sphy.airconcontroller

import com.sphy.airconcontroller.byd.CryptoUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BydApiClientMockServerTest {
    @Test
    fun cryptoHelpers_areStable() {
        assertEquals("E10ADC3949BA59ABBE56E057F20F883E", CryptoUtils.md5Hex("123456"))
        assertTrue(CryptoUtils.sha1Hex("test").isNotBlank())
    }
}

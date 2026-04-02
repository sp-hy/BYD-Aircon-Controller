package com.sphy.airconcontroller.byd

import android.content.Context
import android.util.Base64
import java.nio.ByteBuffer
import java.nio.ByteOrder

interface EnvelopeCodec {
    fun isTableLoaded(): Boolean
    fun encodeEnvelope(plainText: String): String
    fun decodeEnvelope(envelope: String): String
}

class BangcleCodec(private val context: Context) : EnvelopeCodec {
    private val tables: BangcleTables by lazy {
        val raw = context.assets.open("bangcle_tables.bin").use { it.readBytes() }
        loadTables(raw)
    }

    override fun isTableLoaded(): Boolean = true

    override fun encodeEnvelope(plainText: String): String {
        val padded = addPkcs7(plainText.toByteArray(Charsets.UTF_8))
        val cipher = BangcleWhiteBox.encryptCbc(tables, padded, ZERO_IV)
        return "F" + Base64.encodeToString(cipher, Base64.NO_WRAP)
    }

    override fun decodeEnvelope(envelope: String): String {
        val payload = normalizeEnvelopeInput(envelope)
        val decoded = Base64.decode(payload, Base64.DEFAULT)
        val plainPadded = BangcleWhiteBox.decryptCbc(tables, decoded, ZERO_IV)
        return stripPkcs7(plainPadded).toString(Charsets.UTF_8)
    }

    private fun normalizeEnvelopeInput(envelope: String): String {
        var cleaned = envelope.replace(" ", "")
            .replace("\t", "")
            .replace("\n", "")
            .replace("\r", "")
            .trim()
            .replace("-", "+")
            .replace("_", "/")
        require(cleaned.startsWith("F")) { "Bangcle envelope must start with F" }
        cleaned = cleaned.substring(1)
        val rem = cleaned.length % 4
        if (rem != 0) cleaned += "=".repeat(4 - rem)
        return cleaned
    }

    private fun addPkcs7(data: ByteArray, blockSize: Int = 16): ByteArray {
        val remainder = data.size % blockSize
        val padLen = if (remainder == 0) blockSize else blockSize - remainder
        return data + ByteArray(padLen) { padLen.toByte() }
    }

    private fun stripPkcs7(data: ByteArray): ByteArray {
        if (data.isEmpty()) return data
        val pad = data.last().toInt() and 0xFF
        if (pad == 0 || pad > 16 || data.size < pad) return data
        for (i in data.size - pad until data.size) {
            if ((data[i].toInt() and 0xFF) != pad) return data
        }
        return data.copyOfRange(0, data.size - pad)
    }

    private fun loadTables(data: ByteArray): BangcleTables {
        require(data.size >= 72) { "Table file too short" }
        require(String(data.copyOfRange(0, 4), Charsets.US_ASCII) == "BGTB") { "Bad table magic" }
        val version = le16(data, 4)
        require(version == 1) { "Unsupported table version $version" }
        val count = le16(data, 6)
        require(count == 8) { "Expected 8 tables, got $count" }
        val expected = intArrayOf(0x28000, 0x3C000, 0x1000, 0x28000, 0x3C000, 0x1000, 8, 8)
        val chunks = ArrayList<ByteArray>(8)
        for (i in 0 until 8) {
            val idxOff = 8 + i * 8
            val offset = le32(data, idxOff)
            val len = le32(data, idxOff + 4)
            require(len == expected[i]) { "Bad table length at index $i" }
            require(offset + len <= data.size) { "Table index out of bounds" }
            chunks.add(data.copyOfRange(offset, offset + len))
        }
        return BangcleTables(
            invRound = chunks[0],
            invXor = chunks[1],
            invFirst = chunks[2],
            round = chunks[3],
            xor = chunks[4],
            final = chunks[5],
            permDecrypt = chunks[6],
            permEncrypt = chunks[7]
        )
    }

    private fun le16(data: ByteArray, offset: Int): Int {
        return ByteBuffer.wrap(data, offset, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
    }

    private fun le32(data: ByteArray, offset: Int): Int {
        return ByteBuffer.wrap(data, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int
    }

    private companion object {
        val ZERO_IV = ByteArray(16)
    }
}

package com.sphy.airconcontroller.byd

import org.json.JSONObject
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoUtils {
    fun md5Hex(input: String): String = digest("MD5", input).uppercase()
    fun sha1Hex(input: String): String = digest("SHA-1", input)
    fun pwdLoginKey(password: String): String = md5Hex(md5Hex(password))

    fun sha1Mixed(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-1").digest(value.toByteArray(Charsets.UTF_8))
        val mixed = buildString(bytes.size * 2) {
            bytes.forEachIndexed { index, b ->
                val hex = "%02x".format(b.toInt() and 0xFF)
                append(if (index % 2 == 0) hex.uppercase() else hex)
            }
        }
        return mixed.filterIndexed { index, c -> !(c == '0' && index % 2 == 0) }
    }

    fun buildSignString(fields: Map<String, String?>, password: String): String {
        val joined = fields.keys.sorted().joinToString("&") { key ->
            val value = fields[key] ?: "null"
            "$key=$value"
        }
        return "$joined&password=$password"
    }

    fun computeCheckcode(payload: JSONObject): String {
        val md5 = MessageDigest.getInstance("MD5")
            .digest(payload.toString().toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return md5.substring(24, 32) + md5.substring(8, 16) + md5.substring(16, 24) + md5.substring(0, 8)
    }

    fun aesEncryptHex(plaintext: String, keyHex: String): String {
        val key = hexToBytes(keyHex)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(ByteArray(16)))
        return cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8)).toHex().uppercase()
    }

    fun aesDecryptUtf8(cipherHex: String, keyHex: String): String {
        val key = hexToBytes(keyHex)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(ByteArray(16)))
        return cipher.doFinal(hexToBytes(cipherHex)).toString(Charsets.UTF_8)
    }

    private fun digest(algorithm: String, input: String): String {
        val bytes = MessageDigest.getInstance(algorithm).digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.removePrefix("0x").removePrefix("0X")
        require(clean.length % 2 == 0) { "Hex must have even length" }
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}

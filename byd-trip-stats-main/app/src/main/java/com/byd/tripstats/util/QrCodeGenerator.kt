package com.byd.tripstats.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Generates a black-on-white QR bitmap from [content] using ZXing. Used by the Pro
 * licence card to encode a pre-filled `mailto:` — the head unit has no email app, so
 * the user scans it with their phone, which opens their mail composer with the
 * recipient and Vehicle ID already filled in.
 *
 * The bitmap is produced at exactly [sizePx] so it can be drawn 1:1 (no scaling blur
 * that could hurt scannability). Returns null if encoding fails (e.g. content too long).
 */
object QrCodeGenerator {

    fun generate(content: String, sizePx: Int): Bitmap? = runCatching {
        val hints = mapOf(
            EncodeHintType.MARGIN to 1,                              // tight quiet zone
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val w = matrix.width
        val h = matrix.height
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            val offset = y * w
            for (x in 0 until w) {
                pixels[offset + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
            }
        }
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, w, 0, 0, w, h)
        }
    }.getOrNull()
}

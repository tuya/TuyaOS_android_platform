package com.tuya.smartai.demo.utils

import android.graphics.Bitmap
import android.graphics.Color
import com.alibaba.fastjson.JSON
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

object QrCodeUtil {

    @JvmStatic
    fun generateQrCodeBitmap(content: String?, width: Int, height: Int): Bitmap? {
        if (content.isNullOrEmpty()) return null

        val hints = mapOf<EncodeHintType, Any>(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.MARGIN to 1
        )

        return try {
            val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, width, height, hints)
            val pixels = IntArray(width * height) { i ->
                if (bitMatrix.get(i % width, i / width)) Color.BLACK else Color.WHITE
            }
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, width, 0, 0, width, height)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    @JvmStatic
    fun getShortUrl(jsonString: String?): String? {
        if (jsonString.isNullOrBlank()) return null
        return try {
            JSON.parseObject(jsonString)?.getString("shortUrl")
        } catch (e: Exception) {
            System.err.println("Fastjson parsing error: ${e.message}")
            null
        }
    }
}

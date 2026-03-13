package com.nexopos.erp.core.print

import android.graphics.Bitmap
import android.graphics.Canvas
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

class EscPosBuilder(private val charset: Charset = Charset.forName("CP437")) {
    private val buffer = ByteArrayOutputStream()

    fun init(): EscPosBuilder {
        buffer.write(byteArrayOf(0x1B, 0x40))
        return this
    }

    fun alignLeft(): EscPosBuilder {
        buffer.write(byteArrayOf(0x1B, 0x61, 0x00))
        return this
    }

    fun alignCenter(): EscPosBuilder {
        buffer.write(byteArrayOf(0x1B, 0x61, 0x01))
        return this
    }

    fun alignRight(): EscPosBuilder {
        buffer.write(byteArrayOf(0x1B, 0x61, 0x02))
        return this
    }

    fun bold(on: Boolean): EscPosBuilder {
        buffer.write(byteArrayOf(0x1B, 0x45, if (on) 0x01 else 0x00))
        return this
    }

    fun text(s: String): EscPosBuilder {
        buffer.write(s.toByteArray(charset))
        return this
    }

    fun lf(lines: Int = 1): EscPosBuilder {
        repeat(lines) { buffer.write(0x0A) }
        return this
    }

    fun image(bitmap: Bitmap, maxWidthDots: Int): EscPosBuilder {
        val scaled = scaleToWidth(bitmap, maxWidthDots)
        val mono = toMonochrome(scaled)
        val raster = toRasterBytes(mono)
        buffer.write(raster)
        return this
    }

    fun cut(): EscPosBuilder {
        buffer.write(byteArrayOf(0x1D, 0x56, 0x00))
        return this
    }

    fun bytes(): ByteArray = buffer.toByteArray()

    private fun scaleToWidth(src: Bitmap, widthDots: Int): Bitmap {
        val ratio = widthDots.toFloat() / src.width.toFloat()
        val targetW = widthDots
        val targetH = (src.height * ratio).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val scaled = Bitmap.createScaledBitmap(src, targetW, targetH, true)
        canvas.drawBitmap(scaled, 0f, 0f, null)
        return bmp
    }

    private fun toMonochrome(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val srcRow = IntArray(w)
        val outRow = IntArray(w)
        var currentErrors = FloatArray(w + 2)
        var nextErrors = FloatArray(w + 2)
        val threshold = 127.5f

        for (y in 0 until h) {
            src.getPixels(srcRow, 0, w, 0, y, w, 1)
            currentErrors[0] = 0f
            currentErrors[w + 1] = 0f
            for (x in 0 until w) {
                val idx = x + 1
                val pixel = srcRow[x]
                val alpha = ((pixel ushr 24) and 0xFF) / 255f
                val r = ((pixel shr 16) and 0xFF)
                val g = ((pixel shr 8) and 0xFF)
                val b = (pixel and 0xFF)
                val blendedR = (r * alpha) + (255f * (1f - alpha))
                val blendedG = (g * alpha) + (255f * (1f - alpha))
                val blendedB = (b * alpha) + (255f * (1f - alpha))
                val gray = (0.299f * blendedR) + (0.587f * blendedG) + (0.114f * blendedB)
                val value = gray + currentErrors[idx]
                currentErrors[idx] = 0f
                val isBlack = value < threshold
                outRow[x] = if (isBlack) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                val quantError = value - if (isBlack) 0f else 255f

                currentErrors[idx + 1] += quantError * (7f / 16f)
                nextErrors[idx - 1] += quantError * (3f / 16f)
                nextErrors[idx] += quantError * (5f / 16f)
                nextErrors[idx + 1] += quantError * (1f / 16f)
            }
            out.setPixels(outRow, 0, w, 0, y, w, 1)
            val temp = currentErrors
            currentErrors = nextErrors
            nextErrors = temp
            nextErrors.fill(0f)
        }
        return out
    }

    private fun toRasterBytes(bmp: Bitmap): ByteArray {
        val w = bmp.width
        val h = bmp.height
        val bytesPerRow = (w + 7) / 8
        val xL = bytesPerRow and 0xFF
        val xH = (bytesPerRow shr 8) and 0xFF
        val yL = h and 0xFF
        val yH = (h shr 8) and 0xFF
        val header = byteArrayOf(0x1D, 0x76, 0x30, 0x00, xL.toByte(), xH.toByte(), yL.toByte(), yH.toByte())
        val out = ByteArrayOutputStream()
        out.write(header)
        val row = IntArray(w)
        for (y in 0 until h) {
            bmp.getPixels(row, 0, w, 0, y, w, 1)
            var bit = 0
            var byteVal = 0
            for (x in 0 until w) {
                val c = row[x]
                val isBlack = (c and 0x00FFFFFF) == 0x000000
                byteVal = byteVal shl 1
                if (isBlack) byteVal = byteVal or 0x01
                bit++
                if (bit == 8) {
                    out.write(byteVal)
                    bit = 0
                    byteVal = 0
                }
            }
            if (bit != 0) {
                byteVal = byteVal shl (8 - bit)
                out.write(byteVal)
            }
        }
        return out.toByteArray()
    }
}

package com.example.oversee.service

import android.graphics.Bitmap
import java.nio.ByteBuffer

object OcrPreprocessor {

    sealed interface Stage {
        data object Luma : Stage
        data object Otsu : Stage
        data object PHashGate : Stage
        data class Crop(val topInset: Int, val bottomInset: Int) : Stage

        companion object {
            val ALL_NAMES = listOf("Luma", "Otsu", "PHashGate", "Crop")
        }
    }

    // Rec. 601 luma from ARGB_8888 bitmap → ALPHA_8 bitmap
    fun applyLuma(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val out = ByteArray(w * h)
        for (i in pixels.indices) {
            val px = pixels[i]
            val r = (px shr 16) and 0xFF
            val g = (px shr 8) and 0xFF
            val b = px and 0xFF
            out[i] = ((r * 77 + g * 150 + b * 29) ushr 8).toByte()
        }
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8)
        result.copyPixelsFromBuffer(ByteBuffer.wrap(out))
        return result
    }

    // Otsu global threshold on ALPHA_8 bitmap.
    // Skips binarisation when histogram variance is too low (photo/flat image).
    // Returns the same bitmap object if skipped.
    fun applyOtsu(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val buf = ByteBuffer.allocate(w * h)
        bitmap.copyPixelsToBuffer(buf)
        val bytes = buf.array()
        val total = bytes.size

        val hist = IntArray(256)
        for (b in bytes) hist[b.toInt() and 0xFF]++

        // Bimodal safety check: skip Otsu on flat/photo images
        val mean = bytes.fold(0.0) { acc, b -> acc + (b.toInt() and 0xFF) } / total
        val variance = bytes.fold(0.0) { acc, b ->
            val d = (b.toInt() and 0xFF) - mean; acc + d * d
        } / total
        if (variance < 500.0) return bitmap

        // Otsu's method: maximise between-class variance
        var sum = 0
        for (i in 0..255) sum += i * hist[i]
        var sumB = 0; var wB = 0; var maxVar = 0.0; var threshold = 0
        for (i in 0..255) {
            wB += hist[i]
            if (wB == 0) continue
            val wF = total - wB
            if (wF == 0) break
            sumB += i * hist[i]
            val mB = sumB.toDouble() / wB
            val mF = (sum - sumB).toDouble() / wF
            val between = wB.toDouble() * wF * (mB - mF) * (mB - mF)
            if (between > maxVar) { maxVar = between; threshold = i }
        }

        val result = ByteArray(total)
        for (i in bytes.indices) {
            result[i] = if ((bytes[i].toInt() and 0xFF) >= threshold) 0xFF.toByte() else 0
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8)
        out.copyPixelsFromBuffer(ByteBuffer.wrap(result))
        return out
    }

    // 8×8 perceptual hash on an ALPHA_8 bitmap via point-subsampling.
    // Returns a 64-bit Long where bit i is 1 if sample i >= mean.
    fun perceptualHash(bitmap: Bitmap): Long {
        val w = bitmap.width
        val h = bitmap.height
        val buf = ByteBuffer.allocate(w * h)
        bitmap.copyPixelsToBuffer(buf)
        val bytes = buf.array()

        val sampled = ByteArray(64)
        for (row in 0..7) {
            for (col in 0..7) {
                val y = row * h / 8
                val x = col * w / 8
                sampled[row * 8 + col] = bytes[y * w + x]
            }
        }
        val mean = sampled.fold(0) { acc, b -> acc + (b.toInt() and 0xFF) } / 64
        var hash = 0L
        for (i in 0..63) {
            if ((sampled[i].toInt() and 0xFF) >= mean) hash = hash or (1L shl i)
        }
        return hash
    }

    // Extracts raw byte array from an ALPHA_8 bitmap (1 byte per pixel).
    fun extractBytes(bitmap: Bitmap): ByteArray {
        val buf = ByteBuffer.allocate(bitmap.width * bitmap.height)
        bitmap.copyPixelsToBuffer(buf)
        return buf.array()
    }
}

package com.dreamdisplays.util

import java.nio.ByteBuffer

/**
 * Implementation of nearest-neighbor RGBA scaling with aspect-ratio
 * preservation (cover mode). Source/destination are direct ByteBuffers laid out
 * row-major, 4 bytes per pixel.
 */
object ConverterUtil {


    fun scaleRGBA(src: ByteBuffer, srcW: Int, srcH: Int, dst: ByteBuffer, dstW: Int, dstH: Int) {
        require(srcW > 0 && srcH > 0 && dstW > 0 && dstH > 0) { "Image dimensions must be positive" }

        val scale = maxOf(dstW.toDouble() / srcW, dstH.toDouble() / srcH)
        val scaledW = (srcW * scale + 0.5).toInt()
        val scaledH = (srcH * scale + 0.5).toInt()
        val offsetX = (dstW - scaledW) / 2
        val offsetY = (dstH - scaledH) / 2

        // Clear destination (transparent black)
        repeat(dstW * dstH * 4) { i -> dst.put(i, 0) }

        for (y in 0 until dstH) {
            val srcY = ((y - offsetY).toLong() * srcH / scaledH).toInt()
            if (srcY !in 0..<srcH) continue
            val srcRow = srcY * srcW
            val dstRow = y * dstW
            for (x in 0 until dstW) {
                val srcX = ((x - offsetX).toLong() * srcW / scaledW).toInt()
                if (srcX in 0 until srcW) {
                    dst.putInt((dstRow + x) * 4, src.getInt((srcRow + srcX) * 4))
                }
            }
        }
    }
}

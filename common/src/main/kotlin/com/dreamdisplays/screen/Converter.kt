package com.dreamdisplays.screen

import java.nio.ByteBuffer

/**
 * Native (temporary not) image format converter for high-performance pixel operations.
 * Converts RGBA/ABGR formats using native code (temporarily implemented in Java).
 */
object Converter {

    // Java implementation for image scaling with aspect ratio preservation (cover mode)
    private fun scaleRGBAImageJava(
        srcBuffer: ByteBuffer?,
        srcW: Int,
        srcH: Int,
        dstBuffer: ByteBuffer?,
        dstW: Int,
        dstH: Int
    ) {
        require(!(srcBuffer == null || dstBuffer == null)) { "Source and destination buffers cannot be null" }

        require(!(srcW <= 0 || srcH <= 0 || dstW <= 0 || dstH <= 0)) { "Image dimensions must be positive" }

        // Calculate scaling to maintain aspect ratio (cover mode)
        val scaleW = dstW.toDouble() / srcW
        val scaleH = dstH.toDouble() / srcH
        val scale = Math.max(scaleW, scaleH) // Use larger scale to cover entire area
        val scaledW = (srcW * scale + 0.5).toInt()
        val scaledH = (srcH * scale + 0.5).toInt()

        // Calculate offsets to center the image
        val offsetX = (dstW - scaledW) / 2
        val offsetY = (dstH - scaledH) / 2

        // Fill destination with black (transparent)
        for (i in 0 until dstW * dstH * 4) {
            dstBuffer.put(i, 0.toByte())
        }

        // Nearest neighbor scaling with centering
        for (y in 0 until dstH) {
            val srcY = (((y - offsetY) * srcH) / scaledH.toDouble()).toInt()

            if (srcY < 0 || srcY >= srcH) continue

            for (x in 0 until dstW) {
                val srcX = (((x - offsetX) * srcW) / scaledW.toDouble()).toInt()

                if (srcX >= 0 && srcX < srcW) {
                    // Copy 4 bytes (RGBA) from source to destination
                    val srcIdx = (srcY * srcW + srcX) * 4
                    val dstIdx = (y * dstW + x) * 4

                    val pixel = srcBuffer.getInt(srcIdx)
                    dstBuffer.putInt(dstIdx, pixel)
                }
            }
        }
    }

    // Scale RGBA image using nearest neighbor scaling - pure Java implementation
    @JvmStatic
    fun scaleRGBA(
        srcBuffer: ByteBuffer,
        srcW: Int,
        srcH: Int,
        dstBuffer: ByteBuffer,
        dstW: Int,
        dstH: Int
    ) {
        scaleRGBAImageJava(srcBuffer, srcW, srcH, dstBuffer, dstW, dstH)
    }
}

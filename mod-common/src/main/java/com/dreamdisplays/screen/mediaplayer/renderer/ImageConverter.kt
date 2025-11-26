package com.dreamdisplays.screen.mediaplayer.renderer

import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte
import java.nio.ByteBuffer

object ImageConverter {
    fun abgrToRgbaDirect(image: BufferedImage): ByteBuffer {
        val data = (image.raster.dataBuffer as DataBufferByte).data
        val buffer = ByteBuffer.allocateDirect(data.size).order(java.nio.ByteOrder.nativeOrder())

        var i = 0
        val length = data.size
        while (i <= length - 16) {
            // ABGR ABGR ABGR ABGR → RGBA RGBA RGBA RGBA
            val b0 = data[i].toInt() and 0xFF     // A
            val b1 = data[i + 1].toInt() and 0xFF // B
            val b2 = data[i + 2].toInt() and 0xFF // G
            val b3 = data[i + 3].toInt() and 0xFF // R

            val b4 = data[i + 4].toInt() and 0xFF
            val b5 = data[i + 5].toInt() and 0xFF
            val b6 = data[i + 6].toInt() and 0xFF
            val b7 = data[i + 7].toInt() and 0xFF

            val b8 = data[i + 8].toInt() and 0xFF
            val b9 = data[i + 9].toInt() and 0xFF
            val b10 = data[i + 10].toInt() and 0xFF
            val b11 = data[i + 11].toInt() and 0xFF

            val b12 = data[i + 12].toInt() and 0xFF
            val b13 = data[i + 13].toInt() and 0xFF
            val b14 = data[i + 14].toInt() and 0xFF
            val b15 = data[i + 15].toInt() and 0xFF

            buffer.put(b3.toByte()); buffer.put(b2.toByte()); buffer.put(b1.toByte()); buffer.put(b0.toByte())
            buffer.put(b7.toByte()); buffer.put(b6.toByte()); buffer.put(b5.toByte()); buffer.put(b4.toByte())
            buffer.put(b11.toByte()); buffer.put(b10.toByte()); buffer.put(b9.toByte()); buffer.put(b8.toByte())
            buffer.put(b15.toByte()); buffer.put(b14.toByte()); buffer.put(b13.toByte()); buffer.put(b12.toByte())

            i += 16
        }

        while (i < length) {
            buffer.put(data[i + 3]) // R
            buffer.put(data[i + 2]) // G
            buffer.put(data[i + 1]) // B
            buffer.put(data[i])     // A
            i += 4
        }

        buffer.flip()
        return buffer
    }
}

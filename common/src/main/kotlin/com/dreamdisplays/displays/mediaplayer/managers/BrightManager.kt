package com.dreamdisplays.displays.mediaplayer.managers

import java.nio.ByteBuffer
import kotlin.math.min

class BrightManager {
    companion object {
        fun applyBrightnessToBuffer(src: ByteBuffer, dst: ByteBuffer, bytes: Int, brightness: Double) {
            val pixels = bytes ushr 2
            val brightFixed = (brightness * 256.0).toInt()

            val pairs = pixels ushr 1
            val odd = pixels and 1
            if (brightness < 1.0) {
                // Process 2 pixels per iteration when possible
                for (i in 0..<pairs) {
                    // Pixel 1
                    val rgba1 = src.getInt()
                    val r1 = (((rgba1 ushr 24) and 0xFF) * brightFixed) ushr 8
                    val g1 = (((rgba1 ushr 16) and 0xFF) * brightFixed) ushr 8
                    val b1 = (((rgba1 ushr 8) and 0xFF) * brightFixed) ushr 8
                    val a1 = rgba1 and 0xFF

                    // Pixel 2
                    val rgba2 = src.getInt()
                    val r2 = (((rgba2 ushr 24) and 0xFF) * brightFixed) ushr 8
                    val g2 = (((rgba2 ushr 16) and 0xFF) * brightFixed) ushr 8
                    val b2 = (((rgba2 ushr 8) and 0xFF) * brightFixed) ushr 8
                    val a2 = rgba2 and 0xFF

                    // Write both pixels
                    dst.putInt((r1 shl 24) or (g1 shl 16) or (b1 shl 8) or a1)
                    dst.putInt((r2 shl 24) or (g2 shl 16) or (b2 shl 8) or a2)
                }

                // Handle odd pixel
                if (odd != 0) {
                    val rgba = src.getInt()
                    val r = (((rgba ushr 24) and 0xFF) * brightFixed) ushr 8
                    val g = (((rgba ushr 16) and 0xFF) * brightFixed) ushr 8
                    val b = (((rgba ushr 8) and 0xFF) * brightFixed) ushr 8
                    val a = rgba and 0xFF
                    dst.putInt((r shl 24) or (g shl 16) or (b shl 8) or a)
                }
            } else {
                for (i in 0..<pairs) {
                    // Pixel 1
                    val rgba1 = src.getInt()
                    val r1 = min(255, (((rgba1 ushr 24) and 0xFF) * brightFixed) ushr 8)
                    val g1 = min(255, (((rgba1 ushr 16) and 0xFF) * brightFixed) ushr 8)
                    val b1 = min(255, (((rgba1 ushr 8) and 0xFF) * brightFixed) ushr 8)
                    val a1 = rgba1 and 0xFF

                    // Pixel 2
                    val rgba2 = src.getInt()
                    val r2 = min(255, (((rgba2 ushr 24) and 0xFF) * brightFixed) ushr 8)
                    val g2 = min(255, (((rgba2 ushr 16) and 0xFF) * brightFixed) ushr 8)
                    val b2 = min(255, (((rgba2 ushr 8) and 0xFF) * brightFixed) ushr 8)
                    val a2 = rgba2 and 0xFF

                    // Write both pixels
                    dst.putInt((r1 shl 24) or (g1 shl 16) or (b1 shl 8) or a1)
                    dst.putInt((r2 shl 24) or (g2 shl 16) or (b2 shl 8) or a2)
                }

                // Handle odd pixel
                if (odd != 0) {
                    val rgba = src.getInt()
                    val r = min(255, (((rgba ushr 24) and 0xFF) * brightFixed) ushr 8)
                    val g = min(255, (((rgba ushr 16) and 0xFF) * brightFixed) ushr 8)
                    val b = min(255, (((rgba ushr 8) and 0xFF) * brightFixed) ushr 8)
                    val a = rgba and 0xFF
                    dst.putInt((r shl 24) or (g shl 16) or (b shl 8) or a)
                }
            }

            dst.flip()
        }
    }
}

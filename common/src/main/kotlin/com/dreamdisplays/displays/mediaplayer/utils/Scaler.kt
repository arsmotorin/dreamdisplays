package com.dreamdisplays.displays.mediaplayer.utils

import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class Scaler {
    companion object {
        fun scaleRGBA(
            src: ByteBuffer,
            srcW: Int,
            srcH: Int,
            dst: ByteBuffer,
            dstW: Int,
            dstH: Int,
            brightness: Double,
        ) {
            // Calculate scaling factors
            val scaleW = dstW.toDouble() / srcW
            val scaleH = dstH.toDouble() / srcH

            // Take the larger scale to ensure the image fills the dst buffer
            val scale = max(scaleW, scaleH)

            // Calculate the scaled dimensions of the source image
            val scaledW = (srcW * scale + 0.5).toInt()
            val scaledH = (srcH * scale + 0.5).toInt()

            // Calculate offsets to center the image in the dst buffer
            val offsetX = (dstW - scaledW) ushr 1
            val offsetY = (dstH - scaledH) ushr 1

            // Precompute inverse scaling factors in fixed-point 16.16 format
            // int offsetX = (dstW - scaledW) / 2;
            // int offsetY = (dstH - scaledH) / 2;
            val invScaleWFixed = ((srcW shl 16) / scaledW.toDouble()).toInt()
            val invScaleHFixed = ((srcH shl 16) / scaledH.toDouble()).toInt()

            // Determine if brightness adjustment is needed
            val applyBright = abs(brightness - 1.0) >= 1e-5
            // Precompute brightness in fixed-point 8.8 format
            val brightFixed = (brightness * 256).toInt()

            // Prepare dst buffer: clear to black with transparency
            val totalBytes = dstW * dstH * 4 // Total bytes in dst buffer
            val longs = totalBytes ushr 3 // Total longs (8 bytes each)
            val remaining = totalBytes and 7 // Remaining bytes after longs

            dst.clear() // Set position to 0
            for (i in 0..<longs) {
                dst.putLong(0L) // Set 8 bytes to 0 (black pixel)
            }
            for (i in 0..<remaining) {
                dst.put(0.toByte()) // Set remaining bytes to 0
            }
            dst.clear() // Reset position for writing

            // Precompute row byte sizes
            val srcWBytes = srcW shl 2 // srcW * 4
            val dstWBytes = dstW shl 2 // dstW * 4

            // No brightness adjustment needed
            if (!applyBright) {
                // 4 pixels per iteration
                for (y in 0..<dstH) {
                    // Calculate corresponding srcY
                    val srcY = (((y - offsetY) * invScaleHFixed) ushr 16)
                    if (srcY >= srcH) continue  // Skip if out of bounds


                    // TODO: should we also check srcY < 0?
                    val srcRowBase = srcY * srcWBytes // Address start of row in src buffer
                    val dstRowBase = y * dstWBytes // Address start of row in dst buffer

                    var x = 0
                    val xLimit = dstW - 3

                    // 4 pixels at a time
                    while (x < xLimit) {
                        // Check for their coordinates in source image
                        val srcX0 = (((x - offsetX) * invScaleWFixed) ushr 16)
                        val srcX1 = (((x + 1 - offsetX) * invScaleWFixed) ushr 16)
                        val srcX2 = (((x + 2 - offsetX) * invScaleWFixed) ushr 16)
                        val srcX3 = (((x + 3 - offsetX) * invScaleWFixed) ushr 16)

                        // Copy pixels if within bounds
                        if (srcX0 < srcW) {
                            dst.putInt(dstRowBase + (x shl 2), src.getInt(srcRowBase + (srcX0 shl 2)))
                        }
                        if (srcX1 <= srcW) {
                            dst.putInt(dstRowBase + ((x + 1) shl 2), src.getInt(srcRowBase + (srcX1 shl 2)))
                        }
                        if (srcX2 < srcW) {
                            dst.putInt(dstRowBase + ((x + 2) shl 2), src.getInt(srcRowBase + (srcX2 shl 2)))
                        }
                        if (srcX3 < srcW) {
                            dst.putInt(dstRowBase + ((x + 3) shl 2), src.getInt(srcRowBase + (srcX3 shl 2)))
                        }
                        x += 4
                    }

                    // Single pixels remaining
                    while (x < dstW) {
                        val srcX = (((x - offsetX) * invScaleWFixed) ushr 16)
                        if (srcX < srcW) {
                            dst.putInt(dstRowBase + (x shl 2), src.getInt(srcRowBase + (srcX shl 2)))
                        }
                        x++
                    }
                }
                // If brightness < 1.0 (darkening)
            } else if (brightness < 1.0) {
                for (y in 0..<dstH) {
                    // int srcY = (int) (((y - offsetY) * srcH) / (double) scaledH);
                    // if (srcY < 0 || srcY >= srcH) continue;
                    val srcY = (((y - offsetY) * invScaleHFixed) ushr 16)
                    if (srcY >= srcH) continue

                    val srcRowBase = srcY * srcWBytes
                    val dstRowBase = y * dstWBytes

                    var x = 0
                    val xLimit = dstW - 1

                    while (x < xLimit) {
                        val srcX0 = (((x - offsetX) * invScaleWFixed) ushr 16)
                        val srcX1 = (((x + 1 - offsetX) * invScaleWFixed) ushr 16)

                        // 2 pixels at a time
                        if (srcX0 < srcW) {
                            val rgba = src.getInt(srcRowBase + (srcX0 shl 2))
                            val r = (((rgba ushr 24) and 0xFF) * brightFixed) ushr 8
                            val g = (((rgba ushr 16) and 0xFF) * brightFixed) ushr 8
                            val b = (((rgba ushr 8) and 0xFF) * brightFixed) ushr 8
                            val a = rgba and 0xFF
                            dst.putInt(dstRowBase + (x shl 2), (r shl 24) or (g shl 16) or (b shl 8) or a)
                        }

                        if (srcX1 < srcW) {
                            val rgba = src.getInt(srcRowBase + (srcX1 shl 2))
                            val r = (((rgba ushr 24) and 0xFF) * brightFixed) ushr 8
                            val g = (((rgba ushr 16) and 0xFF) * brightFixed) ushr 8
                            val b = (((rgba ushr 8) and 0xFF) * brightFixed) ushr 8
                            val a = rgba and 0xFF
                            dst.putInt(dstRowBase + ((x + 1) shl 2), (r shl 24) or (g shl 16) or (b shl 8) or a)
                        }
                        x += 2
                    }

                    // Single pixel remaining
                    while (x < dstW) {
                        val srcX = (((x - offsetX) * invScaleWFixed) ushr 16)
                        if (srcX < srcW) {
                            val rgba = src.getInt(srcRowBase + (srcX shl 2))
                            val r = (((rgba ushr 24) and 0xFF) * brightFixed) ushr 8
                            val g = (((rgba ushr 16) and 0xFF) * brightFixed) ushr 8
                            val b = (((rgba ushr 8) and 0xFF) * brightFixed) ushr 8
                            val a = rgba and 0xFF
                            dst.putInt(dstRowBase + (x shl 2), (r shl 24) or (g shl 16) or (b shl 8) or a)
                        }
                        x++
                    }
                }
            } else {
                for (y in 0..<dstH) {
                    val srcY = (((y - offsetY) * invScaleHFixed) ushr 16)
                    if (srcY >= srcH) continue

                    val srcRowBase = srcY * srcWBytes
                    val dstRowBase = y * dstWBytes

                    var x = 0
                    val xLimit = dstW - 1

                    while (x < xLimit) {
                        val srcX0 = (((x - offsetX) * invScaleWFixed) ushr 16)
                        val srcX1 = (((x + 1 - offsetX) * invScaleWFixed) ushr 16)

                        // 2 pixels at a time
                        if (srcX0 < srcW) {
                            val rgba = src.getInt(srcRowBase + (srcX0 shl 2))
                            val r = min(255, (((rgba ushr 24) and 0xFF) * brightFixed) ushr 8)
                            val g = min(255, (((rgba ushr 16) and 0xFF) * brightFixed) ushr 8)
                            val b = min(255, (((rgba ushr 8) and 0xFF) * brightFixed) ushr 8)
                            val a = rgba and 0xFF
                            dst.putInt(dstRowBase + (x shl 2), (r shl 24) or (g shl 16) or (b shl 8) or a)
                        }

                        if (srcX1 < srcW) {
                            val rgba = src.getInt(srcRowBase + (srcX1 shl 2))
                            val r = min(255, (((rgba ushr 24) and 0xFF) * brightFixed) ushr 8)
                            val g = min(255, (((rgba ushr 16) and 0xFF) * brightFixed) ushr 8)
                            val b = min(255, (((rgba ushr 8) and 0xFF) * brightFixed) ushr 8)
                            val a = rgba and 0xFF
                            dst.putInt(dstRowBase + ((x + 1) shl 2), (r shl 24) or (g shl 16) or (b shl 8) or a)
                        }
                        x += 2
                    }

                    // Single pixel remaining
                    while (x < dstW) {
                        val srcX = (((x - offsetX) * invScaleWFixed) ushr 16)
                        if (srcX < srcW) {
                            val rgba = src.getInt(srcRowBase + (srcX shl 2))
                            val r = min(255, (((rgba ushr 24) and 0xFF) * brightFixed) ushr 8)
                            val g = min(255, (((rgba ushr 16) and 0xFF) * brightFixed) ushr 8)
                            val b = min(255, (((rgba ushr 8) and 0xFF) * brightFixed) ushr 8)
                            val a = rgba and 0xFF
                            dst.putInt(dstRowBase + (x shl 2), (r shl 24) or (g shl 16) or (b shl 8) or a)
                        }
                        x++
                    }
                }
            }
        }
    }
}
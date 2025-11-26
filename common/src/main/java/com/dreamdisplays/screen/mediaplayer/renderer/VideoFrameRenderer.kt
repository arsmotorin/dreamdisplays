package com.dreamdisplays.screen.mediaplayer.renderer

import org.bytedeco.javacv.Frame
import org.bytedeco.javacv.Java2DFrameConverter
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.nio.ByteBuffer

/**
 * Renders video frames to a specified target resolution with letterboxing.
 */
class VideoFrameRenderer(
    private val targetWidth: Int,
    private val targetHeight: Int
) {

    // Converter to transform Frame to BufferedImage
    private val converter = Java2DFrameConverter()

    @Volatile
    private var preparedBuffer: ByteBuffer? = null

    @Volatile
    private var preparedW = 0

    @Volatile
    private var preparedH = 0

    // Render a video frame to the target resolution
    fun render(frame: Frame?): ByteBuffer? {

        // Return existing buffer if frame is null or has no image
        if (frame?.image == null) return preparedBuffer

        // Convert Frame to BufferedImage
        val src = converter.convert(frame) ?: return preparedBuffer

        // Scale
        val scaled = scaleWithLetterbox(src)

        // Convert ABGR to RGBA and store in preparedBuffer
        preparedBuffer = ImageConverter.abgrToRgbaDirect(scaled)
        preparedW = targetWidth
        preparedH = targetHeight

        // Return the prepared buffer
        return preparedBuffer
    }

    // Get the current prepared buffer and its dimensions
    fun getCurrentBuffer(): ByteBuffer? = preparedBuffer

    // Get the current prepared dimensions
    fun getCurrentDimensions() = preparedW to preparedH

    // Scale the source image to fit the target dimensions
    private fun scaleWithLetterbox(src: BufferedImage): BufferedImage {
        val dst = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_4BYTE_ABGR)
        val g = dst.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        }

        // Scale while preserving aspect ratio
        val scale = maxOf(targetWidth.toDouble() / src.width, targetHeight.toDouble() / src.height)
        val newW = (src.width * scale).toInt()
        val newH = (src.height * scale).toInt()

        val x = (targetWidth - newW) / 2
        val y = (targetHeight - newH) / 2

        g.drawImage(src, x, y, newW, newH, null)
        g.dispose()

        // Return the scaled image
        return dst
    }
}

package com.dreamdisplays.screen.mediaplayer.renderer

import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.GpuTexture
import java.nio.ByteBuffer

/**
 * Uploads texture data to the GPU.
 */
object TextureUploader {

    fun upload(
        texture: GpuTexture,
        buffer: ByteBuffer,
        width: Int,
        height: Int
    ) {
        if (!texture.isClosed && buffer.capacity() >= width * height * 4) {
            buffer.rewind()
            val encoder = RenderSystem.getDevice().createCommandEncoder()
            encoder.writeToTexture(
                texture,
                buffer,
                NativeImage.Format.RGBA,
                0, 0, 0, 0,
                width, height
            )
        }
    }
}

package com.dreamdisplays.displays.mediaplayer.managers

import com.dreamdisplays.displays.mediaplayer.buffer.BufferPreparator
import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.GpuTexture

object FrameManager {
    fun textureFilled(bp: BufferPreparator): Boolean {
        return bp.frameReady && bp.preparedBuffer != null && bp.preparedBuffer!!.remaining() > 0
    }

    fun updateFrame(bp: BufferPreparator, texture: GpuTexture) {
        if (!bp.frameReady) return

        val buf = bp.preparedBuffer ?: return

        val w = bp.preparedW
        val h = bp.preparedH

        if (w != bp.mp.display.textureWidth || h != bp.mp.display.textureHeight) return

        buf.rewind()

        if (!texture.isClosed) {
            RenderSystem.getDevice()
                .createCommandEncoder()
                .writeToTexture(
                    texture, buf, NativeImage.Format.RGBA,
                    0, 0, 0, 0, w, h
                )
        }

        bp.frameReady = false
    }
}

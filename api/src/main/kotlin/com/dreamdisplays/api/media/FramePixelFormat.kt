package com.dreamdisplays.api.media

import com.dreamdisplays.api.DreamDisplaysUnstableApi

/**
 * Platform-free pixel layout of a decoded video frame handed from the media player to the
 * render layer. The Minecraft client maps this to its concrete GL / `NativeImage` formats.
 */
@DreamDisplaysUnstableApi
enum class FramePixelFormat(val bytesPerPixel: Int) {
    /** Single-channel plane of an RGB frame. */
    RGB24(3),

    /** Four-channel plane of an RGBA frame. */
    RGBA32(4),

    /** Single-channel plane of an R8 frame. */
    R8(1),
}

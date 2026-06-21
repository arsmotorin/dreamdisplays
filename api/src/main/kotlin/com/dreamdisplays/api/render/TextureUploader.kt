package com.dreamdisplays.api.render

import com.dreamdisplays.api.media.sink.DecodedVideoFrame

interface TextureUploader : AutoCloseable {
    val supportsAsync: Boolean
    val maxTextureSize: Int

    fun upload(frame: DecodedVideoFrame): TextureHandle
    fun release(handle: TextureHandle)
}

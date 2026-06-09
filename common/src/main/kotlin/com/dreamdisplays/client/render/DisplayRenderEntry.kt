package com.dreamdisplays.client.render

import com.dreamdisplays.api.DisplayBounds
import com.dreamdisplays.api.DisplayId
import com.dreamdisplays.render.api.TextureHandle

data class DisplayRenderEntry(
    val displayId: DisplayId,
    val bounds: DisplayBounds,
    val textureHandle: TextureHandle,
    val brightness: Float,
    val isVisible: Boolean,
)

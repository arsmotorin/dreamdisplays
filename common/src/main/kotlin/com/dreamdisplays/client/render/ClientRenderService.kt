package com.dreamdisplays.client.render

import com.dreamdisplays.api.DisplayId
import com.dreamdisplays.render.api.RenderContext
import com.dreamdisplays.render.api.TextureHandle

interface ClientRenderService {
    fun registerDisplay(entry: DisplayRenderEntry)
    fun unregisterDisplay(displayId: DisplayId)
    fun updateTexture(displayId: DisplayId, handle: TextureHandle)
    fun renderAll(context: RenderContext)
    val registeredCount: Int
}

package com.dreamdisplays.client.render

import com.dreamdisplays.render.api.RenderContext

fun interface RenderHook {
    fun onRender(context: RenderContext)
}

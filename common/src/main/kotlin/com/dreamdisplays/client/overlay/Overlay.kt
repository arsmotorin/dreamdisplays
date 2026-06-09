package com.dreamdisplays.client.overlay

import com.dreamdisplays.api.DisplayId

interface Overlay {
    val displayId: DisplayId
    val isVisible: Boolean
    val bounds: OverlayBounds

    fun render(context: OverlayRenderContext)

    /** @return true if the event was consumed */
    fun onEvent(event: OverlayEvent): Boolean
}

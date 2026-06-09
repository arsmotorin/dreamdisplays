package com.dreamdisplays.client.overlay

import com.dreamdisplays.api.DisplayId

interface OverlayManager {
    fun openPip(displayId: DisplayId): Overlay?
    fun closePip(displayId: DisplayId)
    fun getOverlay(displayId: DisplayId): Overlay?
    fun listOverlays(): List<Overlay>
    fun renderAll(context: OverlayRenderContext)
    fun dispatchEvent(event: OverlayEvent, atX: Float, atY: Float): Boolean
    fun closeAll()

    /** True when no overlays are currently open; lets render hooks skip work cheaply. */
    val isEmpty: Boolean
}

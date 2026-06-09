package com.dreamdisplays.client.overlay

sealed interface OverlayEvent {
    data class MouseMoved(val x: Float, val y: Float) : OverlayEvent
    data class MousePressed(val x: Float, val y: Float, val button: Int) : OverlayEvent
    data class MouseReleased(val x: Float, val y: Float, val button: Int) : OverlayEvent
    data class MouseScrolled(val x: Float, val y: Float, val delta: Float) : OverlayEvent
    data class DragStarted(val x: Float, val y: Float) : OverlayEvent
    data class Dragged(val x: Float, val y: Float, val dx: Float, val dy: Float) : OverlayEvent
    data class DragEnded(val x: Float, val y: Float) : OverlayEvent
    data class CloseRequested(val animated: Boolean = true) : OverlayEvent
    data class SnapRequested(val anchor: OverlayAnchor) : OverlayEvent
}

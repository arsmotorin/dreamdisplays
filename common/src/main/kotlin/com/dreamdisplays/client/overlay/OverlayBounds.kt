package com.dreamdisplays.client.overlay

data class OverlayBounds(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val anchor: OverlayAnchor = OverlayAnchor.FREE,
) {
    val right: Float get() = x + width
    val bottom: Float get() = y + height
    val aspectRatio: Float get() = width / height

    fun contains(px: Float, py: Float): Boolean = px in x..right && py in y..bottom
    fun movedTo(newX: Float, newY: Float): OverlayBounds = copy(x = newX, y = newY)
    fun scaledTo(newWidth: Float, newHeight: Float): OverlayBounds = copy(width = newWidth, height = newHeight)
}

package com.dreamdisplays.client.overlay

enum class OverlayAnchor(val snapX: Float, val snapY: Float) {
    TOP_LEFT(0f, 0f),
    TOP_RIGHT(1f, 0f),
    BOTTOM_LEFT(0f, 1f),
    BOTTOM_RIGHT(1f, 1f),
    FREE(-1f, -1f);

    val isSnapped: Boolean get() = this != FREE
}

package com.dreamdisplays.client.overlay

interface OverlayRenderContext {
    val screenWidth: Int
    val screenHeight: Int
    val scaleFactor: Double
    val partialTick: Float
}

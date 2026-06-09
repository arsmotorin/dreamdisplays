package com.dreamdisplays.api

data class DisplaySettings(
    val volume: Float = 1.0f,
    val quality: String = "auto",
    val brightness: Float = 1.0f,
    val muted: Boolean = false,
    val paused: Boolean = false,
    val renderDistance: Int = 32,
    val syncEnabled: Boolean = true,
    val urlOverride: String? = null,
    val audioTrackName: String? = null,
) {
    init {
        require(volume in 0f..2f) { "Volume must be in [0, 2], got $volume" }
        require(brightness in 0f..2f) { "Brightness must be in [0, 2], got $brightness" }
        require(renderDistance > 0) { "Render distance must be positive" }
    }

    companion object {
        val DEFAULT = DisplaySettings()
    }
}

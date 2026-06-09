package com.dreamdisplays.api

data class Display(
    val id: DisplayId,
    val bounds: DisplayBounds,
    val settings: DisplaySettings,
    val url: String?,
    val state: DisplayRuntimeState,
) {
    val isPlaying: Boolean get() = state is DisplayRuntimeState.Playing
    val isPaused: Boolean get() = state is DisplayRuntimeState.Paused
    val isIdle: Boolean get() = state is DisplayRuntimeState.Idle
    val hasUrl: Boolean get() = !url.isNullOrBlank()
}

package com.dreamdisplays.api

sealed interface DisplayRuntimeState {
    data object Idle : DisplayRuntimeState
    data object OutOfRange : DisplayRuntimeState
    data object Preparing : DisplayRuntimeState

    data class Buffering(val sessionId: String) : DisplayRuntimeState

    data class Playing(
        val sessionId: String,
        val positionMs: Long,
        val durationMs: Long?,
    ) : DisplayRuntimeState

    data class Paused(
        val sessionId: String,
        val positionMs: Long,
    ) : DisplayRuntimeState

    data class Failed(
        val reason: String,
        val retryCount: Int = 0,
        val isFatal: Boolean = false,
    ) : DisplayRuntimeState

    data object Stopped : DisplayRuntimeState

    val isActive: Boolean
        get() = this is Playing || this is Paused || this is Buffering

    val sessionId: String?
        get() = when (this) {
            is Playing -> sessionId
            is Paused -> sessionId
            is Buffering -> sessionId
            else -> null
        }
}

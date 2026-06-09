package com.dreamdisplays.api

sealed interface DisplayEvent {
    val displayId: DisplayId

    data class Created(override val displayId: DisplayId, val display: Display) : DisplayEvent
    data class Removed(override val displayId: DisplayId) : DisplayEvent
    data class SettingsChanged(
        override val displayId: DisplayId,
        val previous: DisplaySettings,
        val current: DisplaySettings,
    ) : DisplayEvent
    data class StateChanged(
        override val displayId: DisplayId,
        val previous: DisplayRuntimeState,
        val current: DisplayRuntimeState,
    ) : DisplayEvent
    data class UrlChanged(override val displayId: DisplayId, val url: String?) : DisplayEvent
    data class MediaError(override val displayId: DisplayId, val reason: String, val isFatal: Boolean) : DisplayEvent
    data class LoadedIntoRange(override val displayId: DisplayId) : DisplayEvent
    data class UnloadedOutOfRange(override val displayId: DisplayId) : DisplayEvent
}

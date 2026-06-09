package com.dreamdisplays.client.popout

sealed interface PopoutEvent {
    data object Opened : PopoutEvent
    data object Closed : PopoutEvent
    data class Resized(val width: Int, val height: Int) : PopoutEvent
    data class FocusGained(val displayId: String) : PopoutEvent
    data class FocusLost(val displayId: String) : PopoutEvent
    data object BackendFailed : PopoutEvent
}

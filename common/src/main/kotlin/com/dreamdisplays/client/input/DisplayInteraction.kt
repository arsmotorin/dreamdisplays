package com.dreamdisplays.client.input

import com.dreamdisplays.api.DisplayId

sealed interface DisplayInteraction {
    data class RightClicked(val displayId: DisplayId) : DisplayInteraction
    data class Looked(val displayId: DisplayId) : DisplayInteraction
    data class LookedAway(val displayId: DisplayId) : DisplayInteraction
    data class HotbarScrolled(val displayId: DisplayId, val delta: Int) : DisplayInteraction
    data class FocusModeToggled(val displayId: DisplayId, val enabled: Boolean) : DisplayInteraction
    data class PopoutRequested(val displayId: DisplayId) : DisplayInteraction
}

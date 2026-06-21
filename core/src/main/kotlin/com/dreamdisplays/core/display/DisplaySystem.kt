package com.dreamdisplays.core.display

import com.dreamdisplays.api.*

interface DisplaySystem :
    DisplayLookup,
    DisplayMutationPort,
    PlaybackPort,
    WatchPartyPort {
    fun recordDisplay(display: Display)
    fun removeDisplay(id: DisplayId)
    fun clearDisplays()
    fun publish(event: DisplayEvent)
}

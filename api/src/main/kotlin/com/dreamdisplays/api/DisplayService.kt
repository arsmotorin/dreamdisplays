package com.dreamdisplays.api

import com.dreamdisplays.api.Display
import com.dreamdisplays.api.DisplayEvent
import com.dreamdisplays.api.DisplayId
import com.dreamdisplays.api.DisplaySettings

/**
 * Represents a display in the system.
 */
interface DisplayService {
    fun getDisplay(id: DisplayId): Display?
    fun listDisplays(): List<Display>
    fun updateSettings(id: DisplayId, settings: DisplaySettings)
    fun setUrl(id: DisplayId, url: String?)
    fun on(listener: (DisplayEvent) -> Unit): AutoCloseable
}

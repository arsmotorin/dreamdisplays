@file:OptIn(DreamDisplaysUnstableApi::class)

package com.dreamdisplays.api

import com.dreamdisplays.core.display.Display
import com.dreamdisplays.core.display.DisplayEvent
import com.dreamdisplays.core.display.DisplayId
import com.dreamdisplays.core.display.DisplaySettings

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

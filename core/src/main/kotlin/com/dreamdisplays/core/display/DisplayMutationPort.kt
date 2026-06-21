package com.dreamdisplays.core.display

import com.dreamdisplays.api.*

interface DisplayMutationPort {
    fun updateSettings(id: DisplayId, settings: DisplaySettings)
    fun setUrl(id: DisplayId, url: String?)
}

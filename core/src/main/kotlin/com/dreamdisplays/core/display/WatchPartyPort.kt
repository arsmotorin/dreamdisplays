package com.dreamdisplays.core.display

import com.dreamdisplays.api.*

import com.dreamdisplays.api.WatchPartySession

interface WatchPartyPort {
    fun start(displayId: DisplayId, url: String?): Boolean
    fun setReady(displayId: DisplayId, ready: Boolean)
    fun begin(displayId: DisplayId)
    fun end(displayId: DisplayId)
    fun restartSession(displayId: DisplayId)
    fun close(displayId: DisplayId)
    fun getSession(displayId: DisplayId): WatchPartySession?
}

package com.dreamdisplays.datatypes

import java.util.*

@JvmRecord
data class SyncPacket(
    @JvmField val id: UUID?,
    @JvmField val isSync: Boolean,
    @JvmField val currentState: Boolean,
    @JvmField val currentTime: Long,
    @JvmField val limitTime: Long
)

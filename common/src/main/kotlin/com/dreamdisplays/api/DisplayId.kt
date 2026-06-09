package com.dreamdisplays.api

import java.util.UUID

@JvmInline
value class DisplayId(val uuid: UUID) {
    override fun toString(): String = uuid.toString()

    companion object {
        fun random(): DisplayId = DisplayId(UUID.randomUUID())
        fun from(string: String): DisplayId = DisplayId(UUID.fromString(string))
        fun fromOrNull(string: String): DisplayId? = runCatching { from(string) }.getOrNull()
    }
}

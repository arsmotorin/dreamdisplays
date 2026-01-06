package com.dreamdisplays.util

import org.jspecify.annotations.NullMarked

/**
 * Enum representing the four cardinal facings.
 */
@NullMarked
enum class Facing {
    NORTH,
    EAST,
    SOUTH,
    WEST;

    fun toPacket(): Byte {
        return this.ordinal.toByte()
    }

    companion object {
        @JvmStatic
        fun Byte.fromPacket(): Facing {
            if (this < 0 || this >= entries.size) throw IllegalArgumentException("Invalid facing ID: $this")
            return entries[toInt()]
        }
    }
}

package com.dreamdisplays.util

import org.jspecify.annotations.NullMarked

/**
 * Enum representing the six possible facings (four cardinal directions + up/down).
 */
@NullMarked
enum class Facing {
    NORTH,
    EAST,
    SOUTH,
    WEST,
    UP,
    DOWN;

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

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
        fun fromPacket(data: Byte): Facing {
            if (data < 0 || data >= entries.size) throw IllegalArgumentException("Invalid facing ID: $data")
            return entries[data.toInt()]
        }
    }
}

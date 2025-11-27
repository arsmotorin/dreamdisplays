package com.dreamdisplays.util

import org.jspecify.annotations.NullMarked

/**
 * The Facing enum represents the four cardinal compass directions: NORTH, EAST, SOUTH, and WEST.
 * This used for screen orientation in the world and for network packet serialization.
 */
@NullMarked
enum class Facing {
    NORTH, EAST, SOUTH, WEST;

    fun toPacket(): Byte {
        return this.ordinal.toByte()
    }

    companion object {
        @JvmStatic
        fun fromPacket(data: Byte): Facing {
            require(!(data < 0 || data >= entries.size)) { "Invalid facing ID: $data" }
            return entries[data.toInt()]
        }
    }
}

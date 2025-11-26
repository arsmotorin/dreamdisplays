package com.dreamdisplays.util

import org.jspecify.annotations.NullMarked

// The Facing enum represents the four cardinal compass directions: NORTH, EAST, SOUTH, and WEST.
// It provides methods to convert between the enum values and their corresponding byte representations
// used in network packets.
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

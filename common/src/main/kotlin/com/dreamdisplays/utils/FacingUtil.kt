package com.dreamdisplays.utils

/** Facing directions for block placement and interaction. */
enum class FacingUtil {
    NORTH, EAST, SOUTH, WEST;

    /** Serializes this facing to a single byte using its ordinal index. */
    fun toPacket(): Byte = ordinal.toByte()

    companion object {
        /** Deserializes a facing from [data] byte; throws [IllegalArgumentException] for out-of-range values. */
        @JvmStatic fun fromPacket(data: Byte): FacingUtil {
            val values = entries
            require(data in values.indices) { "Invalid facing ID: $data" }
            return values[data.toInt()]
        }
    }
}

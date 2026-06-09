package com.dreamdisplays.api

enum class DisplayFacing(val byte: Byte) {
    NORTH(0),
    EAST(1),
    SOUTH(2),
    WEST(3);

    val opposite: DisplayFacing get() = when (this) {
        NORTH -> SOUTH
        SOUTH -> NORTH
        EAST -> WEST
        WEST -> EAST
    }

    companion object {
        fun fromByte(byte: Byte): DisplayFacing =
            entries.firstOrNull { it.byte == byte } ?: error("Unknown facing byte: $byte")
    }
}

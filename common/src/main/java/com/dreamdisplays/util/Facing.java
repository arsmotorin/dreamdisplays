package com.dreamdisplays.util;

import org.jspecify.annotations.NullMarked;

/**
 * Enum representing the six possible facings (four cardinal directions + up/down).
 */
@NullMarked
public enum Facing {
    NORTH,
    EAST,
    SOUTH,
    WEST,
    UP,
    DOWN;

    public static Facing fromPacket(byte data) {
        if (
                data < 0 || data >= values().length
        ) throw new IllegalArgumentException("Invalid facing ID: " + data);
        return values()[data];
    }

    public byte toPacket() {
        return (byte) this.ordinal();
    }
}

package com.dreamdisplays.util;

import org.jspecify.annotations.NullMarked;

// The Facing enum represents the four cardinal compass directions: NORTH, EAST, SOUTH, and WEST.
// It provides methods to convert between the enum values and their corresponding byte representations
// used in network packets.
@NullMarked
public enum Facing {
    NORTH, EAST, SOUTH, WEST;

    public static Facing fromPacket(byte data) {
        if (data < 0 || data >= values().length)
            throw new IllegalArgumentException("Invalid facing ID: " + data);
        return values()[data];
    }

    public byte toPacket() {
        return (byte) this.ordinal();
    }
}

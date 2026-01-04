package com.dreamdisplays;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jspecify.annotations.NullMarked;

/**
 * Sender of custom packets for the mod.
 */
@NullMarked
public interface Mod {
    void sendPacket(CustomPacketPayload packet);
}

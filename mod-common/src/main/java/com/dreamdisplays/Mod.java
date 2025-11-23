package com.dreamdisplays;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jspecify.annotations.NullMarked;

@NullMarked
public interface Mod {
    void sendPacket(CustomPacketPayload packet);
}

package com.dreamdisplays

import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import org.jspecify.annotations.NullMarked

@NullMarked
interface Mod {
    fun sendPacket(packet: CustomPacketPayload)
}

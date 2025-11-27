package com.dreamdisplays

import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import org.jspecify.annotations.NullMarked

/**
 * Mod interface for sending packets.
 */
@NullMarked
interface Mod {
    fun sendPacket(packet: CustomPacketPayload)
}

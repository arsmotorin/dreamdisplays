package com.dreamdisplays.net.common

import com.dreamdisplays.net.common.helpers.createType
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.codec.StreamCodec.of
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type

data class DisplayEnabledPacket(val enabled: Boolean) : CustomPacketPayload {
    override fun type(): Type<out CustomPacketPayload> = PACKET_ID

    companion object {
        val PACKET_ID: Type<DisplayEnabledPacket> = createType("display_enabled")
        val PACKET_CODEC: StreamCodec<FriendlyByteBuf, DisplayEnabledPacket> = of(
            { buf, packet -> buf.writeBoolean(packet.enabled) },
            { buf -> DisplayEnabledPacket(buf.readBoolean()) }
        )
    }
}

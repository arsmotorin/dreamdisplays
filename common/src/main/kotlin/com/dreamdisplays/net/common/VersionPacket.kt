package com.dreamdisplays.net.common

import com.dreamdisplays.net.common.helpers.createType
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.codec.StreamCodec.of
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type

data class VersionPacket(val version: String) : CustomPacketPayload {
    override fun type(): Type<out CustomPacketPayload> = PACKET_ID

    companion object {
        val PACKET_ID: Type<VersionPacket> = createType("version")
        val PACKET_CODEC: StreamCodec<FriendlyByteBuf, VersionPacket> = of(
            { buf, packet -> buf.writeUtf(packet.version) },
            { buf -> VersionPacket(buf.readUtf()) }
        )
    }
}

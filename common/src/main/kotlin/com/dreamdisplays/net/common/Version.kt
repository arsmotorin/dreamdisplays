package com.dreamdisplays.net.common

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

data class Version(val version: String) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = PACKET_ID

    companion object {
        @JvmField
        val PACKET_ID: CustomPacketPayload.Type<Version> = createType("version")

        @JvmField
        val PACKET_CODEC: StreamCodec<FriendlyByteBuf, Version> = StreamCodec.of(
            { buf, packet -> buf.writeUtf(packet.version) },
            { buf -> Version(buf.readUtf()) }
        )
    }
}

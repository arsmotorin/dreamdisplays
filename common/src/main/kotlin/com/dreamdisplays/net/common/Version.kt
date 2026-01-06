package com.dreamdisplays.net.common

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.codec.StreamCodec.*
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type

data class Version(val version: String) : CustomPacketPayload {
    override fun type(): Type<out CustomPacketPayload> = PACKET_ID

    companion object {
        @JvmField
        val PACKET_ID: Type<Version> = createType("version")

        @JvmField
        val PACKET_CODEC: StreamCodec<FriendlyByteBuf, Version> = of(
            { buf, packet -> buf.writeUtf(packet.version) },
            { buf -> Version(buf.readUtf()) }
        )
    }
}

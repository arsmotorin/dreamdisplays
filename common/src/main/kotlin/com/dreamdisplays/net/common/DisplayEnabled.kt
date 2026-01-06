package com.dreamdisplays.net.common

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.codec.StreamCodec.*
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type

data class DisplayEnabled(val enabled: Boolean) : CustomPacketPayload {
    override fun type(): Type<out CustomPacketPayload> = PACKET_ID

    companion object {
        @JvmField
        val PACKET_ID: Type<DisplayEnabled> = createType("display_enabled")

        @JvmField
        val PACKET_CODEC: StreamCodec<FriendlyByteBuf, DisplayEnabled> = of(
            { buf, packet -> buf.writeBoolean(packet.enabled) },
            { buf -> DisplayEnabled(buf.readBoolean()) }
        )
    }
}

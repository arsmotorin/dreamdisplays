package com.dreamdisplays.net.common

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import kotlin.jvm.JvmField

data class DisplayEnabled(val enabled: Boolean) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = PACKET_ID

    companion object {
        @JvmField
        val PACKET_ID: CustomPacketPayload.Type<DisplayEnabled> = createType("display_enabled")
        @JvmField
        val PACKET_CODEC: StreamCodec<FriendlyByteBuf, DisplayEnabled> = StreamCodec.of(
            { buf, packet -> buf.writeBoolean(packet.enabled) },
            { buf -> DisplayEnabled(buf.readBoolean()) }
        )
    }
}

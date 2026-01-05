package com.dreamdisplays.net.s2c

import com.dreamdisplays.net.common.createType
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import kotlin.jvm.JvmField

data class Premium(val premium: Boolean) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = PACKET_ID

    companion object {
        @JvmField
        val PACKET_ID: CustomPacketPayload.Type<Premium> = createType("premium")
        @JvmField
        val PACKET_CODEC: StreamCodec<FriendlyByteBuf, Premium> = StreamCodec.of(
            { buf, packet -> buf.writeBoolean(packet.premium) },
            { buf -> Premium(buf.readBoolean()) }
        )
    }
}

package com.dreamdisplays.net.s2c

import com.dreamdisplays.net.common.createType
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.codec.StreamCodec.*
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type

data class Premium(val premium: Boolean) : CustomPacketPayload {
    override fun type(): Type<out CustomPacketPayload> = PACKET_ID

    companion object {
        @JvmField
        val PACKET_ID: Type<Premium> = createType("premium")

        @JvmField
        val PACKET_CODEC: StreamCodec<FriendlyByteBuf, Premium> = of(
            { buf, packet -> buf.writeBoolean(packet.premium) },
            { buf -> Premium(buf.readBoolean()) }
        )
    }
}

package com.dreamdisplays.net.common

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.codec.StreamCodec.*
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type
import java.util.*

data class Delete(val uuid: UUID) : CustomPacketPayload {
    override fun type(): Type<out CustomPacketPayload> = PACKET_ID

    companion object {
        @JvmField
        val PACKET_ID: Type<Delete> = createType("delete")

        @JvmField
        val PACKET_CODEC: StreamCodec<FriendlyByteBuf, Delete> = of(
            { buf, packet -> buf.writeUUID(packet.uuid) },
            { buf -> Delete(buf.readUUID()) }
        )
    }
}

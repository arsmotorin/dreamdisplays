package com.dreamdisplays.net.common

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import java.util.*

data class Delete(val uuid: UUID) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = PACKET_ID

    companion object {
        @JvmField
        val PACKET_ID: CustomPacketPayload.Type<Delete> = createType("delete")

        @JvmField
        val PACKET_CODEC: StreamCodec<FriendlyByteBuf, Delete> = StreamCodec.of(
            { buf, packet -> buf.writeUUID(packet.uuid) },
            { buf -> Delete(buf.readUUID()) }
        )
    }
}

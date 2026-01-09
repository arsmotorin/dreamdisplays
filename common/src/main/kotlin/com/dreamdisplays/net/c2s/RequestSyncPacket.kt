package com.dreamdisplays.net.c2s

import com.dreamdisplays.net.common.helpers.createType
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.codec.StreamCodec.of
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type
import java.util.*

data class RequestSyncPacket(val uuid: UUID) : CustomPacketPayload {
    override fun type(): Type<out CustomPacketPayload> = PACKET_ID

    companion object {
        val PACKET_ID: Type<RequestSyncPacket> = createType("req_sync")
        val PACKET_CODEC: StreamCodec<FriendlyByteBuf, RequestSyncPacket> = of(
            { buf, packet -> buf.writeUUID(packet.uuid) },
            { buf -> RequestSyncPacket(buf.readUUID()) }
        )
    }
}

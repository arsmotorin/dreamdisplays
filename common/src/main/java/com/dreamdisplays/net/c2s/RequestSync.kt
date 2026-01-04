package com.dreamdisplays.net.c2s

import com.dreamdisplays.net.common.createType
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import java.util.UUID
import kotlin.jvm.JvmField

data class RequestSync(val uuid: UUID) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = PACKET_ID

    companion object {
        @JvmField
        val PACKET_ID: CustomPacketPayload.Type<RequestSync> = createType("req_sync")
        @JvmField
        val PACKET_CODEC: StreamCodec<FriendlyByteBuf, RequestSync> = StreamCodec.of(
            { buf, packet -> buf.writeUUID(packet.uuid) },
            { buf -> RequestSync(buf.readUUID()) }
        )
    }
}

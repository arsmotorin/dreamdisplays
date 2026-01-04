package com.dreamdisplays.net.c2s

import com.dreamdisplays.net.common.createType
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import java.util.UUID
import kotlin.jvm.JvmField

data class Report(val uuid: UUID) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = PACKET_ID

    companion object {
        @JvmField
        val PACKET_ID: CustomPacketPayload.Type<Report> = createType("report")
        @JvmField
        val PACKET_CODEC: StreamCodec<FriendlyByteBuf, Report> = StreamCodec.of(
            { buf, packet -> buf.writeUUID(packet.uuid) },
            { buf -> Report(buf.readUUID()) }
        )
    }
}

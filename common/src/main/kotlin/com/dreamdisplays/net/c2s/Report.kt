package com.dreamdisplays.net.c2s

import com.dreamdisplays.net.common.createType
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.codec.StreamCodec.*
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type
import java.util.*

data class Report(val uuid: UUID) : CustomPacketPayload {
    override fun type(): Type<out CustomPacketPayload> = PACKET_ID

    companion object {
        @JvmField
        val PACKET_ID: Type<Report> = createType("report")

        @JvmField
        val PACKET_CODEC: StreamCodec<FriendlyByteBuf, Report> = of(
            { buf, packet -> buf.writeUUID(packet.uuid) },
            { buf -> Report(buf.readUUID()) }
        )
    }
}

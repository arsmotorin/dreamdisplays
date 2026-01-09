package com.dreamdisplays.net.common

import com.dreamdisplays.net.common.helpers.createType
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.codec.StreamCodec.of
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type
import java.util.*

data class DeletePacket(val uuid: UUID) : CustomPacketPayload {
    override fun type(): Type<out CustomPacketPayload> = PACKET_ID

    companion object {
        @JvmField
        val PACKET_ID: Type<DeletePacket> = createType("delete")

        @JvmField
        val PACKET_CODEC: StreamCodec<FriendlyByteBuf, DeletePacket> = of(
            { buf, packet -> buf.writeUUID(packet.uuid) },
            { buf -> DeletePacket(buf.readUUID()) }
        )
    }
}

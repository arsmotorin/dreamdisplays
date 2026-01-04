package com.dreamdisplays.net.common

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import java.util.UUID
import kotlin.jvm.JvmField

data class Sync(
    val uuid: UUID,
    val isSync: Boolean,
    val currentState: Boolean,
    val currentTime: Long,
    val limitTime: Long
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = PACKET_ID

    companion object {
        @JvmField
        val PACKET_ID: CustomPacketPayload.Type<Sync> = createType("sync")
        @JvmField
        val PACKET_CODEC: StreamCodec<FriendlyByteBuf, Sync> = StreamCodec.of(
            { buf, packet ->
                buf.writeUUID(packet.uuid)
                buf.writeBoolean(packet.isSync)
                buf.writeBoolean(packet.currentState)
                buf.writeVarLong(packet.currentTime)
                buf.writeVarLong(packet.limitTime)
            },
            { buf ->
                Sync(
                    buf.readUUID(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readVarLong(),
                    buf.readVarLong()
                )
            }
        )
    }
}

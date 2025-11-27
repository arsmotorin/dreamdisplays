package com.dreamdisplays.net

import com.dreamdisplays.Initializer
import net.minecraft.core.UUIDUtil
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import org.jspecify.annotations.NullMarked
import java.util.*

/**
 * Packet for synchronizing display state.
 */
@NullMarked
@JvmRecord
data class Sync(
    val id: UUID, val isSync: Boolean, val currentState: Boolean, val currentTime: Long,
    val limitTime: Long
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> {
        return PACKET_ID
    }

    companion object {
        @JvmField
        val PACKET_ID: CustomPacketPayload.Type<Sync> = CustomPacketPayload.Type<Sync>(
            Identifier.fromNamespaceAndPath(
                Initializer.MOD_ID, "sync"
            )
        )

        @JvmField
        val PACKET_CODEC: StreamCodec<FriendlyByteBuf, Sync> = StreamCodec.of(
            { buf: FriendlyByteBuf?, packet: Sync? ->
                UUIDUtil.STREAM_CODEC.encode(buf!!, packet!!.id)
                ByteBufCodecs.BOOL.encode(buf, packet.isSync)
                ByteBufCodecs.BOOL.encode(buf, packet.currentState)
                ByteBufCodecs.VAR_LONG.encode(buf, packet.currentTime)
                ByteBufCodecs.VAR_LONG.encode(buf, packet.limitTime)
            },
            { buf: FriendlyByteBuf? ->
                val id = UUIDUtil.STREAM_CODEC.decode(buf!!)
                val isSync = ByteBufCodecs.BOOL.decode(buf)
                val currentState = ByteBufCodecs.BOOL.decode(buf)
                val currentTime = ByteBufCodecs.VAR_LONG.decode(buf)
                val limitTime = ByteBufCodecs.VAR_LONG.decode(buf)
                Sync(id, isSync, currentState, currentTime, limitTime)
            })
    }
}

package com.dreamdisplays.net

import com.dreamdisplays.Initializer
import net.minecraft.core.UUIDUtil
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import org.jspecify.annotations.NullMarked
import java.util.*

/**
 * Packet for sending a request for display synchronization.
 */
@NullMarked
@JvmRecord
data class RequestSync(val id: UUID) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> {
        return PACKET_ID
    }

    companion object {
        @JvmField
        val PACKET_ID: CustomPacketPayload.Type<RequestSync> = CustomPacketPayload.Type<RequestSync>(
            Identifier.fromNamespaceAndPath(
                Initializer.MOD_ID, "req_sync"
            )
        )

        @JvmField
        val PACKET_CODEC: StreamCodec<FriendlyByteBuf, RequestSync> = StreamCodec.of<FriendlyByteBuf, RequestSync>(
            { buf: FriendlyByteBuf?, packet: RequestSync? ->
                UUIDUtil.STREAM_CODEC.encode(
                    buf!!,
                    packet!!.id
                )
            },
            { buf: FriendlyByteBuf? ->
                val id = UUIDUtil.STREAM_CODEC.decode(buf!!)
                RequestSync(id)
            })
    }
}

package com.dreamdisplays.net

import com.dreamdisplays.Initializer
import com.dreamdisplays.util.Facing
import com.dreamdisplays.util.Facing.Companion.fromPacket
import net.minecraft.core.UUIDUtil
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import org.joml.Vector3i
import org.jspecify.annotations.NullMarked
import java.util.*

/**
 * Packet for sending display information.
 */
@NullMarked
@JvmRecord
data class Info(
    val id: UUID,
    val ownerId: UUID,
    val pos: Vector3i,
    val width: Int,
    val height: Int,
    val url: String,
    val facing: Facing,
    val isSync: Boolean,
    val lang: String
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> {
        return PACKET_ID
    }

    companion object {
        @JvmField
        val PACKET_ID: CustomPacketPayload.Type<Info> = CustomPacketPayload.Type<Info>(
            Identifier.fromNamespaceAndPath(
                Initializer.MOD_ID, "display_info"
            )
        )

        @JvmField
        val PACKET_CODEC: StreamCodec<FriendlyByteBuf, Info> = StreamCodec.of<FriendlyByteBuf, Info>(
            { buf: FriendlyByteBuf?, packet: Info? ->
                UUIDUtil.STREAM_CODEC.encode(buf!!, packet!!.id)
                UUIDUtil.STREAM_CODEC.encode(buf, packet.ownerId)

                ByteBufCodecs.VAR_INT.encode(buf, packet.pos.x())
                ByteBufCodecs.VAR_INT.encode(buf, packet.pos.y())
                ByteBufCodecs.VAR_INT.encode(buf, packet.pos.z())

                ByteBufCodecs.VAR_INT.encode(buf, packet.width)
                ByteBufCodecs.VAR_INT.encode(buf, packet.height)

                ByteBufCodecs.STRING_UTF8.encode(buf, packet.url)

                ByteBufCodecs.BYTE.encode(buf, packet.facing.toPacket())
                ByteBufCodecs.BOOL.encode(buf, packet.isSync)
                ByteBufCodecs.STRING_UTF8.encode(buf, packet.lang)
            },
            { buf: FriendlyByteBuf? ->
                val id = UUIDUtil.STREAM_CODEC.decode(buf!!)
                val ownerId = UUIDUtil.STREAM_CODEC.decode(buf)

                val x = ByteBufCodecs.VAR_INT.decode(buf)
                val y = ByteBufCodecs.VAR_INT.decode(buf)
                val z = ByteBufCodecs.VAR_INT.decode(buf)
                val pos = Vector3i(x, y, z)

                val width = ByteBufCodecs.VAR_INT.decode(buf)
                val height = ByteBufCodecs.VAR_INT.decode(buf)

                val url = ByteBufCodecs.STRING_UTF8.decode(buf)

                val facingByte = ByteBufCodecs.BYTE.decode(buf)
                val facing = fromPacket(facingByte)

                val isSync = ByteBufCodecs.BOOL.decode(buf)
                val lang = ByteBufCodecs.STRING_UTF8.decode(buf)
                Info(id, ownerId, pos, width, height, url, facing, isSync, lang)
            }
        )
    }
}

package com.dreamdisplays.net.s2c

import com.dreamdisplays.net.common.helpers.createType
import com.dreamdisplays.util.Facing
import com.dreamdisplays.util.Facing.Companion.fromPacket
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.codec.StreamCodec.of
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type
import org.joml.Vector3i
import java.util.*

data class DisplayInfoPacket(
    val uuid: UUID,
    val ownerUuid: UUID,
    val pos: Vector3i,
    val width: Int,
    val height: Int,
    val url: String,
    val facing: Facing,
    val isSync: Boolean,
    val lang: String,
) : CustomPacketPayload {
    override fun type(): Type<out CustomPacketPayload> = PACKET_ID

    companion object {
        val PACKET_ID: Type<DisplayInfoPacket> = createType("display_info")
        val PACKET_CODEC: StreamCodec<FriendlyByteBuf, DisplayInfoPacket> = of(
            { buf, packet ->
                buf.writeUUID(packet.uuid)
                buf.writeUUID(packet.ownerUuid)
                buf.writeVarInt(packet.pos.x())
                buf.writeVarInt(packet.pos.y())
                buf.writeVarInt(packet.pos.z())
                buf.writeVarInt(packet.width)
                buf.writeVarInt(packet.height)
                buf.writeUtf(packet.url)
                buf.writeByte(packet.facing.toPacket().toInt())
                buf.writeBoolean(packet.isSync)
                buf.writeUtf(packet.lang)
            },
            { buf ->
                DisplayInfoPacket(
                    buf.readUUID(),
                    buf.readUUID(),
                    Vector3i(
                        buf.readVarInt(),
                        buf.readVarInt(),
                        buf.readVarInt()
                    ),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readUtf(),
                    buf.readByte().fromPacket(),
                    buf.readBoolean(),
                    buf.readUtf()
                )
            }
        )
    }
}

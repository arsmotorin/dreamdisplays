package com.dreamdisplays.net.s2c

import com.dreamdisplays.net.common.createType
import com.dreamdisplays.util.Facing
import com.dreamdisplays.util.Facing.Companion.fromPacket
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.codec.StreamCodec.*
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type
import org.joml.Vector3i
import java.util.*

data class DisplayInfo(
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
        @JvmField
        val PACKET_ID: Type<DisplayInfo> = createType("display_info")

        @JvmField
        val PACKET_CODEC: StreamCodec<FriendlyByteBuf, DisplayInfo> = of(
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
                DisplayInfo(
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

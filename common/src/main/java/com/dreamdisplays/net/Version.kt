package com.dreamdisplays.net

import com.dreamdisplays.Initializer
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import org.jspecify.annotations.NullMarked

// Packet for sending mod version information
@NullMarked
@JvmRecord
data class Version(val version: String) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> {
        return PACKET_ID
    }

    companion object {
        @JvmField
        val PACKET_ID: CustomPacketPayload.Type<Version> = CustomPacketPayload.Type<Version>(
            Identifier.fromNamespaceAndPath(
                Initializer.MOD_ID, "version"
            )
        )

        @JvmField
        val PACKET_CODEC: StreamCodec<FriendlyByteBuf, Version> = StreamCodec.of<FriendlyByteBuf, Version>(
            { buf: FriendlyByteBuf?, packet: Version? ->
                ByteBufCodecs.STRING_UTF8.encode(
                    buf!!,
                    packet!!.version
                )
            },
            { buf: FriendlyByteBuf? ->
                val version = ByteBufCodecs.STRING_UTF8.decode(buf!!)
                Version(version)
            })
    }
}

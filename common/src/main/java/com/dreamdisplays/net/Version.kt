package com.dreamdisplays.net

import com.dreamdisplays.Initializer
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import org.jspecify.annotations.NullMarked

/**
 * Packet for mod version info.
 * This packet is sent from the client to the server upon joining, so the server can log the mod versions
 * and warn about outdated versions.
 *
 * In v2.0.0 there are breaking changes, so players can't join servers with v1.0.x versions.
 */
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

        // TODO: add version compatibility checks (for Arsenii)
    }
}

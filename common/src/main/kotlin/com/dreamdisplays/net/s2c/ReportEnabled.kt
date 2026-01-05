package com.dreamdisplays.net.s2c

import com.dreamdisplays.net.common.createType
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

data class ReportEnabled(val enabled: Boolean) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = PACKET_ID

    companion object {
        @JvmField
        val PACKET_ID: CustomPacketPayload.Type<ReportEnabled> = createType("report_enabled")

        @JvmField
        val PACKET_CODEC: StreamCodec<FriendlyByteBuf, ReportEnabled> = StreamCodec.of(
            { buf, packet -> buf.writeBoolean(packet.enabled) },
            { buf -> ReportEnabled(buf.readBoolean()) }
        )
    }
}

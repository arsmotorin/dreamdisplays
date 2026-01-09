package com.dreamdisplays.net.s2c

import com.dreamdisplays.net.common.helpers.createType
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.codec.StreamCodec.of
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type

data class ReportEnabledPacket(val enabled: Boolean) : CustomPacketPayload {
    override fun type(): Type<out CustomPacketPayload> = PACKET_ID

    companion object {
        val PACKET_ID: Type<ReportEnabledPacket> = createType("report_enabled")
        val PACKET_CODEC: StreamCodec<FriendlyByteBuf, ReportEnabledPacket> = of(
            { buf, packet -> buf.writeBoolean(packet.enabled) },
            { buf -> ReportEnabledPacket(buf.readBoolean()) }
        )
    }
}

package com.dreamdisplays.net;

import com.dreamdisplays.Initializer;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.util.UUID;

// Packet for reporting a display
@NullMarked
public record Report(UUID id) implements CustomPacketPayload {
    public static final Type<Report> PACKET_ID =
        new Type<>(Identifier.fromNamespaceAndPath(Initializer.MOD_ID, "report"));

    public static final StreamCodec<FriendlyByteBuf, Report> PACKET_CODEC =
        StreamCodec.of(
            (buf, packet) -> UUIDUtil.STREAM_CODEC.encode(buf, packet.id()),
            (buf) -> {
                UUID id = UUIDUtil.STREAM_CODEC.decode(buf);
                return new Report(id);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return PACKET_ID;
    }
}

package com.dreamdisplays.net;

import com.dreamdisplays.Initializer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

// Packet for sending mod version information
@NullMarked
public record Version(String version) implements CustomPacketPayload {
    public static final Type<Version> PACKET_ID =
        new Type<>(Identifier.fromNamespaceAndPath(Initializer.MOD_ID, "version"));

    public static final StreamCodec<FriendlyByteBuf, Version> PACKET_CODEC =
        StreamCodec.of(
            (buf, packet) -> ByteBufCodecs.STRING_UTF8.encode(buf, packet.version()),
            (buf) -> {
                String version = ByteBufCodecs.STRING_UTF8.decode(buf);
                return new Version(version);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return PACKET_ID;
    }
}

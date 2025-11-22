package com.dreamdisplays.net;

import com.dreamdisplays.PlatformlessInitializer;

import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// Packet for deleting a display
public record DeletePacket(UUID id) implements CustomPacketPayload {
    public static final Type<DeletePacket> PACKET_ID =
            new Type<>(ResourceLocation.fromNamespaceAndPath(PlatformlessInitializer.MOD_ID, "delete"));

    public static final StreamCodec<FriendlyByteBuf, DeletePacket> PACKET_CODEC =
            StreamCodec.of(
                    (buf, packet) -> UUIDUtil.STREAM_CODEC.encode(buf, packet.id()),
                    (buf) -> {
                        UUID id = UUIDUtil.STREAM_CODEC.decode(buf);
                        return new DeletePacket(id);
                    });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return PACKET_ID;
    }
}
package com.dreamdisplays.net;

import com.dreamdisplays.PlatformlessInitializer;

import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

// Packet for requesting synchronization of a display
@NullMarked
public record RequestSyncPacket(UUID id) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RequestSyncPacket> PACKET_ID =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(PlatformlessInitializer.MOD_ID, "req_sync"));

    public static final StreamCodec<FriendlyByteBuf, RequestSyncPacket> PACKET_CODEC =
            StreamCodec.of(
                    (buf, packet) -> UUIDUtil.STREAM_CODEC.encode(buf, packet.id()),
                    (buf) -> {
                        UUID id = UUIDUtil.STREAM_CODEC.decode(buf);
                        return new RequestSyncPacket(id);
                    });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return PACKET_ID;
    }
}

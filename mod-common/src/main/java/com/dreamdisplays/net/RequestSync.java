package com.dreamdisplays.net;

import com.dreamdisplays.Initializer;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.util.UUID;

// Packet for requesting synchronization of a display
@NullMarked
public record RequestSync(UUID id) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RequestSync> PACKET_ID =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Initializer.MOD_ID, "req_sync"));

    public static final StreamCodec<FriendlyByteBuf, RequestSync> PACKET_CODEC =
            StreamCodec.of(
                    (buf, packet) -> UUIDUtil.STREAM_CODEC.encode(buf, packet.id()),
                    (buf) -> {
                        UUID id = UUIDUtil.STREAM_CODEC.decode(buf);
                        return new RequestSync(id);
                    });

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return PACKET_ID;
    }
}

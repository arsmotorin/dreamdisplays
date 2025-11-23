package com.dreamdisplays.net;

import com.dreamdisplays.PlatformlessInitializer;

import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

// Packet for synchronizing the playback state of a display
@NullMarked
public record SyncPacket(UUID id, boolean isSync, boolean currentState, long currentTime, long limitTime) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SyncPacket> PACKET_ID =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(PlatformlessInitializer.MOD_ID, "sync"));

    public static final StreamCodec<FriendlyByteBuf, SyncPacket> PACKET_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {
                        UUIDUtil.STREAM_CODEC.encode(buf, packet.id());
                        ByteBufCodecs.BOOL.encode(buf, packet.isSync());
                        ByteBufCodecs.BOOL.encode(buf, packet.currentState());
                        ByteBufCodecs.VAR_LONG.encode(buf, packet.currentTime());
                        ByteBufCodecs.VAR_LONG.encode(buf, packet.limitTime());
                    },
                    (buf) -> {
                        UUID id = UUIDUtil.STREAM_CODEC.decode(buf);

                        boolean isSync = ByteBufCodecs.BOOL.decode(buf);
                        boolean currentState = ByteBufCodecs.BOOL.decode(buf);
                        long currentTime = ByteBufCodecs.VAR_LONG.decode(buf);
                        long limitTime = ByteBufCodecs.VAR_LONG.decode(buf);

                        return new SyncPacket(id, isSync, currentState, currentTime, limitTime);
                    });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return PACKET_ID;
    }
}

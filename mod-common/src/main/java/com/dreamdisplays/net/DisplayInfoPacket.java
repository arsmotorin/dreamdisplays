package com.dreamdisplays.net;

import org.joml.Vector3i;
import com.dreamdisplays.PlatformlessInitializer;
import com.dreamdisplays.util.Facing;

import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

// Packet for sending display information
public record DisplayInfoPacket(UUID id, UUID ownerId, Vector3i pos, int width, int height, String url, Facing facing, boolean isSync, String lang) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<DisplayInfoPacket> PACKET_ID =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(PlatformlessInitializer.MOD_ID, "display_info"));

    public static final StreamCodec<FriendlyByteBuf, DisplayInfoPacket> PACKET_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {
                        UUIDUtil.STREAM_CODEC.encode(buf, packet.id());
                        UUIDUtil.STREAM_CODEC.encode(buf, packet.ownerId());

                        ByteBufCodecs.VAR_INT.encode(buf, packet.pos().x());
                        ByteBufCodecs.VAR_INT.encode(buf, packet.pos().y());
                        ByteBufCodecs.VAR_INT.encode(buf, packet.pos().z());

                        ByteBufCodecs.VAR_INT.encode(buf, packet.width());
                        ByteBufCodecs.VAR_INT.encode(buf, packet.height());

                        ByteBufCodecs.STRING_UTF8.encode(buf, packet.url());

                        ByteBufCodecs.BYTE.encode(buf, packet.facing().toPacket());
                        ByteBufCodecs.BOOL.encode(buf, packet.isSync());

                        ByteBufCodecs.STRING_UTF8.encode(buf, packet.lang());
                    },
                    (buf) -> {
                        UUID id = UUIDUtil.STREAM_CODEC.decode(buf);
                        UUID ownerId = UUIDUtil.STREAM_CODEC.decode(buf);

                        int x = ByteBufCodecs.VAR_INT.decode(buf);
                        int y = ByteBufCodecs.VAR_INT.decode(buf);
                        int z = ByteBufCodecs.VAR_INT.decode(buf);
                        Vector3i pos = new Vector3i(x, y, z);

                        int width = ByteBufCodecs.VAR_INT.decode(buf);
                        int height = ByteBufCodecs.VAR_INT.decode(buf);

                        String url = ByteBufCodecs.STRING_UTF8.decode(buf);

                        byte facingByte = ByteBufCodecs.BYTE.decode(buf);
                        Facing facing = Facing.fromPacket(facingByte);

                        boolean isSync = ByteBufCodecs.BOOL.decode(buf);
                        String lang = ByteBufCodecs.STRING_UTF8.decode(buf);

                        return new DisplayInfoPacket(id, ownerId, pos, width, height, url, facing, isSync, lang);
                    }
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return PACKET_ID;
    }
}

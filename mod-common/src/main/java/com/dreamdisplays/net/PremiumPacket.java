package com.dreamdisplays.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import com.dreamdisplays.PlatformlessInitializer;

// Packet for indicating premium status
public record PremiumPacket(boolean premium) implements CustomPacketPayload {
    public static final Type<PremiumPacket> PACKET_ID =
            new Type<>(Identifier.fromNamespaceAndPath(PlatformlessInitializer.MOD_ID, "premium"));

    public static final StreamCodec<FriendlyByteBuf, PremiumPacket> PACKET_CODEC =
            StreamCodec.of(
                    (buf, packet) -> ByteBufCodecs.BOOL.encode(buf, packet.premium()),
                    (buf) -> {
                        boolean premium = ByteBufCodecs.BOOL.decode(buf);
                        return new PremiumPacket(premium);
                    });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return PACKET_ID;
    }
}

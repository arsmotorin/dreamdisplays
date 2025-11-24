package com.dreamdisplays.net;

import com.dreamdisplays.Initializer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

// Packet for indicating premium status
@NullMarked
public record Premium(boolean premium) implements CustomPacketPayload {
    public static final Type<Premium> PACKET_ID =
        new Type<>(Identifier.fromNamespaceAndPath(Initializer.MOD_ID, "premium"));

    public static final StreamCodec<FriendlyByteBuf, Premium> PACKET_CODEC =
        StreamCodec.of(
            (buf, packet) -> ByteBufCodecs.BOOL.encode(buf, packet.premium()),
            (buf) -> {
                boolean premium = ByteBufCodecs.BOOL.decode(buf);
                return new Premium(premium);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return PACKET_ID;
    }
}

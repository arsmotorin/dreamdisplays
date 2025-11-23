package com.dreamdisplays;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.dreamdisplays.net.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import com.dreamdisplays.net.*;
import com.dreamdisplays.render.ScreenWorldRenderer;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class DreamDisplaysMod implements ClientModInitializer, Mod {
    @Override
    public void onInitializeClient() {
        PlatformlessInitializer.onModInit(this);

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> dispatcher.register(
                LiteralArgumentBuilder.<FabricClientCommandSource>literal("displays")
                        .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("off")
                                .executes((context) -> {
                                    PlatformlessInitializer.displaysEnabled = false;
                                    return 1;
                                })
                        )
                        .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("on")
                                .executes((context) -> {
                                    PlatformlessInitializer.displaysEnabled = true;
                                    return 1;
                                })
                        )
        ));

        PayloadTypeRegistry.playS2C().register(DisplayInfoPacket.PACKET_ID, DisplayInfoPacket.PACKET_CODEC);

        PayloadTypeRegistry.playS2C().register(SyncPacket.PACKET_ID, SyncPacket.PACKET_CODEC);
        PayloadTypeRegistry.playC2S().register(SyncPacket.PACKET_ID, SyncPacket.PACKET_CODEC);

        PayloadTypeRegistry.playC2S().register(RequestSyncPacket.PACKET_ID, RequestSyncPacket.PACKET_CODEC);

        PayloadTypeRegistry.playC2S().register(DeletePacket.PACKET_ID, DeletePacket.PACKET_CODEC);
        PayloadTypeRegistry.playC2S().register(ReportPacket.PACKET_ID, ReportPacket.PACKET_CODEC);
        PayloadTypeRegistry.playC2S().register(VersionPacket.PACKET_ID, VersionPacket.PACKET_CODEC);

        PayloadTypeRegistry.playS2C().register(DeletePacket.PACKET_ID, DeletePacket.PACKET_CODEC);
        PayloadTypeRegistry.playS2C().register(PremiumPacket.PACKET_ID, PremiumPacket.PACKET_CODEC);


        ClientPlayNetworking.registerGlobalReceiver(DisplayInfoPacket.PACKET_ID, (payload, unused) -> PlatformlessInitializer.onDisplayInfoPacket(payload));
        ClientPlayNetworking.registerGlobalReceiver(PremiumPacket.PACKET_ID, (payload, unused) -> PlatformlessInitializer.onPremiumPacket(payload));
        ClientPlayNetworking.registerGlobalReceiver(DeletePacket.PACKET_ID, (deletePacket, unused) -> PlatformlessInitializer.onDeletePacket(deletePacket));

        ClientPlayNetworking.registerGlobalReceiver(SyncPacket.PACKET_ID, (payload, unused) -> PlatformlessInitializer.onSyncPacket(payload));

        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null || minecraft.player == null) {
                return;
            }
            PoseStack matrices = context.matrices();
            Camera camera = context.gameRenderer().getMainCamera();
            ScreenWorldRenderer.render(matrices, camera);
        });


        ClientTickEvents.END_CLIENT_TICK.register(PlatformlessInitializer::onEndTick);

        ClientLifecycleEvents.CLIENT_STOPPING.register(minecraftClient -> PlatformlessInitializer.onStop());
    }

    @Override
    public void sendPacket(CustomPacketPayload packet) {
        ClientPlayNetworking.send(packet);
    }
}

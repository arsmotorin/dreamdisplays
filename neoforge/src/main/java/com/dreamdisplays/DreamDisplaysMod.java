package com.dreamdisplays;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.dreamdisplays.net.*;
import com.dreamdisplays.render.ScreenWorldRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@NullMarked
@Mod(value = DreamDisplaysMod.MOD_ID, dist = Dist.CLIENT)
public class DreamDisplaysMod {

    public static final String MOD_ID = "dreamdisplays";

    public DreamDisplaysMod(IEventBus modEventBus) {
        modEventBus.addListener(this::registerPayloads);
        NeoForge.EVENT_BUS.register(this);
    }

    public void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(MOD_ID).optional().versioned("1");
        registrar.playBidirectional(DeletePacket.PACKET_ID, DeletePacket.PACKET_CODEC,
            (serverPayload, ctx) -> {
            },
            (clientPayload, ctx) -> PlatformlessInitializer.onDeletePacket(clientPayload)
        );
        registrar.playToClient(DisplayInfoPacket.PACKET_ID, DisplayInfoPacket.PACKET_CODEC,
            (payload, ctx) -> PlatformlessInitializer.onDisplayInfoPacket(payload));

        registrar.playToClient(SyncPacket.PACKET_ID, SyncPacket.PACKET_CODEC,
            (payload, ctx) -> PlatformlessInitializer.onSyncPacket(payload));

        registrar.playToClient(PremiumPacket.PACKET_ID, PremiumPacket.PACKET_CODEC,
            (payload, ctx) -> PlatformlessInitializer.onPremiumPacket(payload));

        registrar.playToServer(RequestSyncPacket.PACKET_ID, RequestSyncPacket.PACKET_CODEC, (p, c) -> {
        });
        registrar.playToServer(ReportPacket.PACKET_ID, ReportPacket.PACKET_CODEC, (p, c) -> {
        });
        registrar.playToServer(VersionPacket.PACKET_ID, VersionPacket.PACKET_CODEC, (p, c) -> {
        });
    }


    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        PlatformlessInitializer.onEndTick(Minecraft.getInstance());
    }

    @SubscribeEvent
    public void onClientStop(ClientPlayerNetworkEvent.LoggingOut event) {
        PlatformlessInitializer.onStop();
    }

    @SubscribeEvent
    public void onRenderLevelAfterEntities(RenderLevelStageEvent.AfterEntities event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        PoseStack poseStack = event.getPoseStack();
        Camera camera = mc.gameRenderer.getMainCamera();
        ScreenWorldRenderer.render(poseStack, camera);
    }

    @SubscribeEvent
    public void registerCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
            LiteralArgumentBuilder.<CommandSourceStack>literal("displays")
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("off")
                    .executes(ctx -> {
                        PlatformlessInitializer.displaysEnabled = false;
                        LocalPlayer p = Minecraft.getInstance().player;
                        if (p != null)
                            p.displayClientMessage(Component.literal("Displays disabled"), false);
                        return 1;
                    }))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("on")
                    .executes(ctx -> {
                        PlatformlessInitializer.displaysEnabled = true;
                        LocalPlayer p = Minecraft.getInstance().player;
                        if (p != null) p.displayClientMessage(Component.literal("Displays enabled"), false);
                        return 1;
                    }))
        );
    }

    public void sendPacket(CustomPacketPayload packet) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            connection.send(packet);
        }
    }
}

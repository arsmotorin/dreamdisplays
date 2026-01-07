package com.dreamdisplays

import com.dreamdisplays.net.c2s.ReportPacket
import com.dreamdisplays.net.c2s.RequestSyncPacket
import com.dreamdisplays.net.common.DeletePacket
import com.dreamdisplays.net.common.DisplayEnabledPacket
import com.dreamdisplays.net.common.SyncPacket
import com.dreamdisplays.net.common.VersionPacket
import com.dreamdisplays.net.s2c.DisplayInfoPacket
import com.dreamdisplays.net.s2c.PremiumPacket
import com.dreamdisplays.net.s2c.ReportEnabledPacket
import com.dreamdisplays.net.s2c.CeilingFloorSupportPacket
import com.dreamdisplays.render.ScreenRenderer
import com.dreamdisplays.screen.managers.ScreenManager
import net.minecraft.client.Minecraft
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.IEventBus
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.RenderLevelStageEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import org.jspecify.annotations.NullMarked

@NullMarked
@Mod(value = DreamDisplaysMod.MOD_ID, dist = [Dist.CLIENT])
class DreamDisplaysMod(modEventBus: IEventBus) : ModPacketSender {

    init {
        ModInitializer.onModInit(this)
        modEventBus.addListener(this::registerPayloads)
        NeoForge.EVENT_BUS.register(this)
    }

    fun registerPayloads(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar(MOD_ID).optional().versioned("1")

        registrar.playBidirectional(
            DeletePacket.PACKET_ID,
            DeletePacket.PACKET_CODEC,
            { _, _ -> },
            { clientPayload, _ -> ModInitializer.onDeletePacket(clientPayload) }
        )

        registrar.playToClient(
            DisplayInfoPacket.PACKET_ID,
            DisplayInfoPacket.PACKET_CODEC
        ) { payload, _ -> ModInitializer.onDisplayInfoPacket(payload) }

        registrar.playToClient(
            PremiumPacket.PACKET_ID,
            PremiumPacket.PACKET_CODEC
        ) { payload, _ -> ModInitializer.onPremiumPacket(payload) }

        registrar.playToClient(
            DisplayEnabledPacket.PACKET_ID,
            DisplayEnabledPacket.PACKET_CODEC
        ) { payload, _ -> ModInitializer.onDisplayEnabledPacket(payload) }

        registrar.playToClient(
            ReportEnabledPacket.PACKET_ID,
            ReportEnabledPacket.PACKET_CODEC
        ) { payload, _ -> ModInitializer.onReportEnabledPacket(payload) }

        registrar.playToClient(
            CeilingFloorSupportPacket.PACKET_ID,
            CeilingFloorSupportPacket.PACKET_CODEC
        ) { payload, _ -> ModInitializer.onCeilingFloorSupportPacket(payload) }

        registrar.playBidirectional(
            SyncPacket.PACKET_ID,
            SyncPacket.PACKET_CODEC,
            { _, _ -> },
            { clientPayload, _ -> ModInitializer.onSyncPacket(clientPayload) }
        )

        registrar.playToServer(
            RequestSyncPacket.PACKET_ID,
            RequestSyncPacket.PACKET_CODEC
        ) { _, _ -> }

        registrar.playToServer(
            ReportPacket.PACKET_ID,
            ReportPacket.PACKET_CODEC
        ) { _, _ -> }

        registrar.playToServer(
            VersionPacket.PACKET_ID,
            VersionPacket.PACKET_CODEC
        ) { _, _ -> }
    }

    @SubscribeEvent
    fun onClientTick(event: ClientTickEvent.Post) {
        ModInitializer.onEndTick(Minecraft.getInstance())
    }

    @SubscribeEvent
    fun onClientStop(event: ClientPlayerNetworkEvent.LoggingOut) {
        ScreenManager.saveAllScreens()
        ScreenManager.unloadAllDisplays()
    }

    @SubscribeEvent
    fun onClientStopping(event: ClientPlayerNetworkEvent.LoggingOut) {
        ModInitializer.onStop()
    }

    @SubscribeEvent
    fun onRenderLevelAfterEntities(event: RenderLevelStageEvent.AfterEntities) {
        val mc = Minecraft.getInstance()
        if (mc.level == null || mc.player == null) return

        val poseStack = event.poseStack
        val camera = mc.gameRenderer.mainCamera
        ScreenRenderer.render(poseStack, camera)
    }

    @SubscribeEvent
    fun onClientLogin(event: ClientPlayerNetworkEvent.LoggingIn) {
        val mc = Minecraft.getInstance()
        if (mc.level != null && mc.player != null) {
            val serverId = if (mc.hasSingleplayerServer()) {
                "singleplayer"
            } else {
                mc.currentServer?.ip ?: "unknown"
            }
            ScreenManager.loadScreensForServer(serverId)
        }
    }

    override fun sendPacket(packet: CustomPacketPayload) {
        val connection = Minecraft.getInstance().connection
        connection?.send(packet)
    }

    companion object {
        const val MOD_ID = "dreamdisplays"
    }
}

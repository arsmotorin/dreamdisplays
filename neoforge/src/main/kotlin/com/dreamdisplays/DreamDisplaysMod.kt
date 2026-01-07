package com.dreamdisplays

import com.dreamdisplays.net.c2s.Report
import com.dreamdisplays.net.c2s.RequestSync
import com.dreamdisplays.net.common.Delete
import com.dreamdisplays.net.common.DisplayEnabled
import com.dreamdisplays.net.common.Sync
import com.dreamdisplays.net.common.Version
import com.dreamdisplays.net.s2c.DisplayInfo
import com.dreamdisplays.net.s2c.Premium
import com.dreamdisplays.net.s2c.ReportEnabled
import com.dreamdisplays.render.ScreenRenderer
import com.dreamdisplays.screen.Manager
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
class DreamDisplaysMod(modEventBus: IEventBus) : com.dreamdisplays.Mod {

    init {
        Initializer.onModInit(this)
        modEventBus.addListener(this::registerPayloads)
        NeoForge.EVENT_BUS.register(this)
    }

    fun registerPayloads(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar(MOD_ID).optional().versioned("1")
        registrar.playBidirectional(
            Delete.PACKET_ID,
            Delete.PACKET_CODEC,
            { _, _ -> },
            { clientPayload, _ -> Initializer.onDeletePacket(clientPayload) }
        )
        registrar.playToClient(
            DisplayInfo.PACKET_ID,
            DisplayInfo.PACKET_CODEC
        ) { payload, _ -> Initializer.onDisplayInfoPacket(payload) }

        registrar.playToClient(
            Premium.PACKET_ID,
            Premium.PACKET_CODEC
        ) { payload, _ -> Initializer.onPremiumPacket(payload) }

        registrar.playToClient(
            DisplayEnabled.PACKET_ID,
            DisplayEnabled.PACKET_CODEC
        ) { payload, _ -> Initializer.onDisplayEnabledPacket(payload) }

        registrar.playToClient(
            ReportEnabled.PACKET_ID,
            ReportEnabled.PACKET_CODEC
        ) { payload, _ -> Initializer.onReportEnabledPacket(payload) }

        registrar.playBidirectional(
            Sync.PACKET_ID,
            Sync.PACKET_CODEC,
            { _, _ -> },
            { clientPayload, _ -> Initializer.onSyncPacket(clientPayload) }
        )

        registrar.playToServer(
            RequestSync.PACKET_ID,
            RequestSync.PACKET_CODEC
        ) { _, _ -> }
        registrar.playToServer(
            Report.PACKET_ID,
            Report.PACKET_CODEC
        ) { _, _ -> }
        registrar.playToServer(
            Version.PACKET_ID,
            Version.PACKET_CODEC
        ) { _, _ -> }
    }

    @SubscribeEvent
    fun onClientTick(event: ClientTickEvent.Post) {
        Initializer.onEndTick(Minecraft.getInstance())
    }

    @SubscribeEvent
    fun onClientStop(event: ClientPlayerNetworkEvent.LoggingOut) {
        Manager.saveAllScreens()
        Manager.unloadAll()
    }

    @SubscribeEvent
    fun onClientStopping(event: ClientPlayerNetworkEvent.LoggingOut) {
        Initializer.onStop()
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
            val serverId =
                if (mc.hasSingleplayerServer()) "singleplayer" else (if (mc.currentServer != null) mc.currentServer!!.ip else "unknown")
            Manager.loadScreensForServer(serverId)
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

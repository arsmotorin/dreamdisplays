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
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.client.Minecraft
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import org.jspecify.annotations.NullMarked

@NullMarked
class DreamDisplaysMod : ClientModInitializer, Mod {

    override fun onInitializeClient() {
        Initializer.onModInit(this)

        PayloadTypeRegistry.playS2C().register(
            DisplayInfo.PACKET_ID,
            DisplayInfo.PACKET_CODEC
        )

        PayloadTypeRegistry.playS2C().register(
            Sync.PACKET_ID,
            Sync.PACKET_CODEC
        )

        PayloadTypeRegistry.playS2C().register(
            Premium.PACKET_ID,
            Premium.PACKET_CODEC
        )

        PayloadTypeRegistry.playS2C().register(
            Delete.PACKET_ID,
            Delete.PACKET_CODEC
        )

        PayloadTypeRegistry.playS2C().register(
            DisplayEnabled.PACKET_ID,
            DisplayEnabled.PACKET_CODEC
        )

        PayloadTypeRegistry.playS2C().register(
            ReportEnabled.PACKET_ID,
            ReportEnabled.PACKET_CODEC
        )

        PayloadTypeRegistry.playC2S().register(
            Sync.PACKET_ID,
            Sync.PACKET_CODEC
        )

        PayloadTypeRegistry.playC2S().register(
            RequestSync.PACKET_ID,
            RequestSync.PACKET_CODEC
        )

        PayloadTypeRegistry.playC2S().register(
            Delete.PACKET_ID,
            Delete.PACKET_CODEC
        )
        PayloadTypeRegistry.playC2S().register(
            Report.PACKET_ID,
            Report.PACKET_CODEC
        )
        PayloadTypeRegistry.playC2S().register(
            Version.PACKET_ID,
            Version.PACKET_CODEC
        )

        ClientPlayNetworking.registerGlobalReceiver(
            DisplayInfo.PACKET_ID
        ) { payload, _ -> Initializer.onDisplayInfoPacket(payload) }
        ClientPlayNetworking.registerGlobalReceiver(
            Premium.PACKET_ID
        ) { payload, _ -> Initializer.onPremiumPacket(payload) }
        ClientPlayNetworking.registerGlobalReceiver(
            Delete.PACKET_ID
        ) { deletePacket, _ -> Initializer.onDeletePacket(deletePacket) }

        ClientPlayNetworking.registerGlobalReceiver(
            DisplayEnabled.PACKET_ID
        ) { payload, _ -> Initializer.onDisplayEnabledPacket(payload) }

        ClientPlayNetworking.registerGlobalReceiver(
            Sync.PACKET_ID
        ) { payload, _ -> Initializer.onSyncPacket(payload) }

        ClientPlayNetworking.registerGlobalReceiver(
            ReportEnabled.PACKET_ID
        ) { payload, _ -> Initializer.onReportEnabledPacket(payload) }

        WorldRenderEvents.AFTER_ENTITIES.register(WorldRenderEvents.AfterEntities { context ->
            val minecraft = Minecraft.getInstance()
            if (minecraft.level == null || minecraft.player == null) {
                return@AfterEntities
            }
            val matrices = context.matrices()
            val camera = context.gameRenderer().mainCamera
            ScreenRenderer.render(matrices, camera)
        })

        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { Initializer.onEndTick(it) })

        // Load displays when joining a world
        ClientPlayConnectionEvents.JOIN.register(ClientPlayConnectionEvents.Join { _, _, client ->
            if (client.level != null && client.player != null) {
                // Use server address as server ID for local singleplayer worlds
                // TODO: add support for singleplayer in the future.
                val serverId =
                    if (client.hasSingleplayerServer()) "singleplayer" else (if (client.currentServer != null) client.currentServer!!.ip else "unknown")
                Manager.loadScreensForServer(serverId)
            }
        })

        ClientPlayConnectionEvents.DISCONNECT.register(ClientPlayConnectionEvents.Disconnect { _, _ ->
            Manager.saveAllScreens()
            Manager.unloadAll()
            Initializer.onStop()
        })
    }

    override fun sendPacket(packet: CustomPacketPayload) {
        ClientPlayNetworking.send(packet)
    }
}

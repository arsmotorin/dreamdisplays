package com.dreamdisplays

import com.dreamdisplays.Initializer.onDeletePacket
import com.dreamdisplays.Initializer.onDisplayEnabledPacket
import com.dreamdisplays.Initializer.onDisplayInfoPacket
import com.dreamdisplays.Initializer.onEndTick
import com.dreamdisplays.Initializer.onModInit
import com.dreamdisplays.Initializer.onPremiumPacket
import com.dreamdisplays.Initializer.onReportEnabledPacket
import com.dreamdisplays.Initializer.onSyncPacket
import com.dreamdisplays.net.c2s.Report
import com.dreamdisplays.net.c2s.RequestSync
import com.dreamdisplays.net.common.Delete
import com.dreamdisplays.net.common.DisplayEnabled
import com.dreamdisplays.net.common.Sync
import com.dreamdisplays.net.common.Version
import com.dreamdisplays.net.s2c.DisplayInfo
import com.dreamdisplays.net.s2c.Premium
import com.dreamdisplays.net.s2c.ReportEnabled
import com.dreamdisplays.render.ScreenRenderer.render
import com.dreamdisplays.screen.Manager.loadScreensForServer
import com.dreamdisplays.screen.Manager.saveAllScreens
import com.dreamdisplays.screen.Manager.unloadAll
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.*
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.*
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents.*
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playC2S
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C
import net.minecraft.client.Minecraft.getInstance
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import org.jspecify.annotations.NullMarked

@NullMarked
class DreamDisplaysMod : ClientModInitializer, Mod {

    override fun onInitializeClient() {
        onModInit(this)

        playS2C().register(
            DisplayInfo.PACKET_ID,
            DisplayInfo.PACKET_CODEC
        )

        playS2C().register(
            Sync.PACKET_ID,
            Sync.PACKET_CODEC
        )

        playS2C().register(
            Premium.PACKET_ID,
            Premium.PACKET_CODEC
        )

        playS2C().register(
            Delete.PACKET_ID,
            Delete.PACKET_CODEC
        )

        playS2C().register(
            DisplayEnabled.PACKET_ID,
            DisplayEnabled.PACKET_CODEC
        )

        playS2C().register(
            ReportEnabled.PACKET_ID,
            ReportEnabled.PACKET_CODEC
        )

        playC2S().register(
            Sync.PACKET_ID,
            Sync.PACKET_CODEC
        )

        playC2S().register(
            RequestSync.PACKET_ID,
            RequestSync.PACKET_CODEC
        )

        playC2S().register(
            Delete.PACKET_ID,
            Delete.PACKET_CODEC
        )
        playC2S().register(
            Report.PACKET_ID,
            Report.PACKET_CODEC
        )
        playC2S().register(
            Version.PACKET_ID,
            Version.PACKET_CODEC
        )

        registerGlobalReceiver(
            DisplayInfo.PACKET_ID
        ) { payload, _ -> onDisplayInfoPacket(payload) }
        registerGlobalReceiver(
            Premium.PACKET_ID
        ) { payload, _ -> onPremiumPacket(payload) }
        registerGlobalReceiver(
            Delete.PACKET_ID
        ) { deletePacket, _ -> onDeletePacket(deletePacket) }

        registerGlobalReceiver(
            DisplayEnabled.PACKET_ID
        ) { payload, _ -> onDisplayEnabledPacket(payload) }

        registerGlobalReceiver(
            Sync.PACKET_ID
        ) { payload, _ -> onSyncPacket(payload) }

        registerGlobalReceiver(
            ReportEnabled.PACKET_ID
        ) { payload, _ -> onReportEnabledPacket(payload) }

        AFTER_ENTITIES.register(AfterEntities { context ->
            val minecraft = getInstance()
            if (minecraft.level == null || minecraft.player == null) {
                return@AfterEntities
            }
            val matrices = context.matrices()
            val camera = context.gameRenderer().mainCamera
            render(matrices, camera)
        })

        END_CLIENT_TICK.register(EndTick { onEndTick(it) })

        // Load displays when joining a world
        JOIN.register(Join { _, _, client ->
            if (client.level != null && client.player != null) {
                // Use server address as server ID for local singleplayer worlds
                // TODO: add support for singleplayer in the future.
                val serverId =
                    if (client.hasSingleplayerServer()) "singleplayer" else (if (client.currentServer != null) client.currentServer!!.ip else "unknown")
                loadScreensForServer(serverId)
            }
        })

        DISCONNECT.register(Disconnect { _, _ ->
            saveAllScreens()
            unloadAll()
            Initializer.onStop()
        })
    }

    override fun sendPacket(packet: CustomPacketPayload) {
        send(packet)
    }
}

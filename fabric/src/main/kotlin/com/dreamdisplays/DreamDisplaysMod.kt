package com.dreamdisplays

import com.dreamdisplays.ModInitializer.onDisplayInfoPacket
import com.dreamdisplays.ModInitializer.onModInit
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
import com.dreamdisplays.render.ScreenRenderer.render
import com.dreamdisplays.screen.managers.ScreenManager.loadScreensForServer
import com.dreamdisplays.screen.managers.ScreenManager.saveAllScreens
import com.dreamdisplays.screen.managers.ScreenManager.unloadAllDisplays
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
class DreamDisplaysMod : ClientModInitializer, ModPacketSender {

    override fun onInitializeClient() {
        onModInit(this)

        playS2C().register(
            DisplayInfoPacket.PACKET_ID,
            DisplayInfoPacket.PACKET_CODEC
        )

        playS2C().register(
            SyncPacket.PACKET_ID,
            SyncPacket.PACKET_CODEC
        )

        playS2C().register(
            PremiumPacket.PACKET_ID,
            PremiumPacket.PACKET_CODEC
        )

        playS2C().register(
            DeletePacket.PACKET_ID,
            DeletePacket.PACKET_CODEC
        )

        playS2C().register(
            DisplayEnabledPacket.PACKET_ID,
            DisplayEnabledPacket.PACKET_CODEC
        )

        playS2C().register(
            ReportEnabledPacket.PACKET_ID,
            ReportEnabledPacket.PACKET_CODEC
        )

        playS2C().register(
            CeilingFloorSupportPacket.PACKET_ID,
            CeilingFloorSupportPacket.PACKET_CODEC
        )

        playC2S().register(
            SyncPacket.PACKET_ID,
            SyncPacket.PACKET_CODEC
        )

        playC2S().register(
            RequestSyncPacket.PACKET_ID,
            RequestSyncPacket.PACKET_CODEC
        )

        playC2S().register(
            DeletePacket.PACKET_ID,
            DeletePacket.PACKET_CODEC
        )
        playC2S().register(
            ReportPacket.PACKET_ID,
            ReportPacket.PACKET_CODEC
        )
        playC2S().register(
            VersionPacket.PACKET_ID,
            VersionPacket.PACKET_CODEC
        )

        registerGlobalReceiver(
            DisplayInfoPacket.PACKET_ID
        ) { payload, _ -> onDisplayInfoPacket(payload) }
        registerGlobalReceiver(
            PremiumPacket.PACKET_ID
        ) { payload, _ -> ModInitializer.onPremiumPacket(payload) }
        registerGlobalReceiver(
            DeletePacket.PACKET_ID
        ) { deletePacket, _ -> ModInitializer.onDeletePacket(deletePacket) }

        registerGlobalReceiver(
            DisplayEnabledPacket.PACKET_ID
        ) { payload, _ -> ModInitializer.onDisplayEnabledPacket(payload) }

        registerGlobalReceiver(
            SyncPacket.PACKET_ID
        ) { payload, _ -> ModInitializer.onSyncPacket(payload) }

        registerGlobalReceiver(
            ReportEnabledPacket.PACKET_ID
        ) { payload, _ -> ModInitializer.onReportEnabledPacket(payload) }

        registerGlobalReceiver(
            CeilingFloorSupportPacket.PACKET_ID
        ) { payload, _ -> ModInitializer.onCeilingFloorSupportPacket(payload) }

        AFTER_ENTITIES.register(AfterEntities { context ->
            val minecraft = getInstance()
            if (minecraft.level == null || minecraft.player == null) {
                return@AfterEntities
            }
            val matrices = context.matrices()
            val camera = context.gameRenderer().mainCamera
            render(matrices, camera)
        })

        END_CLIENT_TICK.register(EndTick { ModInitializer.onEndTick(it) })

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
            unloadAllDisplays()
            ModInitializer.onStop()
        })
    }

    override fun sendPacket(packet: CustomPacketPayload) {
        send(packet)
    }
}

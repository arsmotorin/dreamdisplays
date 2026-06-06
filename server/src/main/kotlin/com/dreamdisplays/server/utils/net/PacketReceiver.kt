package com.dreamdisplays.server.utils.net

import com.dreamdisplays.server.Main
import com.dreamdisplays.server.datatypes.PaperDisplayData
import com.dreamdisplays.server.datatypes.SyncData
import com.dreamdisplays.server.managers.DisplayManager
import com.dreamdisplays.server.managers.StateManager
import com.dreamdisplays.server.managers.PlayerManager
import com.dreamdisplays.server.meta.Scheduler
import com.dreamdisplays.server.utils.MessageUtil
import com.dreamdisplays.server.utils.YouTubeUtil
import io.github.arsmotorin.ofrat.PaperOnly
import org.semver4j.Semver
import com.google.gson.Gson
import net.kyori.adventure.text.TextReplacementConfig
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import org.bukkit.entity.Player
import org.bukkit.plugin.messaging.PluginMessageListener
import org.jspecify.annotations.NullMarked
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.util.*

/**
 * Handles incoming channels from clients. This is the main entry point for all packet handling.
 */
@PaperOnly @NullMarked class PacketReceiver(private val plugin: Main) : PluginMessageListener {
    private val logger = LoggerFactory.getLogger("DreamDisplays/PacketReceiver")
    private val gson by lazy { Gson() }
    private val maxVersionBytes = 128

    /** Routes an incoming plugin message to the per-channel handler. */
    override fun onPluginMessageReceived(channel: String, player: Player, message: ByteArray) {
        when (channel) {
            "dreamdisplays:sync" -> handleSyncPacket(player, message)
            "dreamdisplays:req_sync" -> handleRequestSync(player, message)
            "dreamdisplays:delete" -> handleDelete(player, message)
            "dreamdisplays:report" -> handleReport(player, message)
            "dreamdisplays:version" -> handleVersion(player, message)
            "dreamdisplays:display_enabled" -> handleDisplayEnabled(player, message)
            "dreamdisplays:set_video" -> handleSetVideo(player, message)
            "dreamdisplays:set_locked" -> handleSetLocked(player, message)
        }
    }

    /** Decodes a sync packet from [player] and forwards it to [StateManager.processSyncPacket]. */
    private fun handleSyncPacket(player: Player, message: ByteArray) {
        runCatching {
            DataInputStream(ByteArrayInputStream(message)).use { input ->
                val syncData = SyncData(
                    input.readUUID(),
                    input.readBoolean(),
                    input.readBoolean(),
                    input.readVarLong(),
                    input.readVarLong()
                )
                StateManager.processSyncPacket(syncData, player)
            }
        }.onFailure { e ->
            logger.warn("Failed to decode sync packet", e)
        }
    }

    /** Replies to a client `req_sync` packet with the current authoritative sync state. */
    private fun handleRequestSync(player: Player, message: ByteArray) {
        readUUIDPacket(message)?.let { displayId ->
            StateManager.sendSyncPacket(displayId, player)
        }
    }

    /** Handles a client-requested deletion, enforcing owner-or-permission check. */
    private fun handleDelete(player: Player, message: ByteArray) {
        readUUIDPacket(message)?.let { displayId ->
            val displayData = DisplayManager.getDisplayData(displayId)
                ?: return@let MessageUtil.sendMessage(player, "noDisplay")

            if (displayData.ownerId != player.uniqueId &&
                !player.hasPermission(Main.config.permissions.delete)
            ) {
                MessageUtil.sendMessage(player, "displayCommandMissingPermission")
                return@let
            }

            DisplayManager.delete(displayId)
            MessageUtil.sendMessage(player, "displayDeleted")
        }
    }

    /** Forwards a client report request to [DisplayManager.report]. */
    private fun handleReport(player: Player, message: ByteArray) {
        readUUIDPacket(message)?.let { displayId ->
            DisplayManager.report(displayId, player)
        }
    }

    /** Records the player's reported mod version and triggers the initial sync and update checks. */
    private fun handleVersion(player: Player, message: ByteArray) {
        runCatching {
            val version = readVersionString(message)
            logger.info("${player.name} joined with Dream Displays $version.")

            initializePlayer(player, version)
            checkForUpdates(player, version)
        }.onFailure { e ->
            logger.warn("Failed to process version packet", e)
        }
    }

    /** Persists a client toggle for whether [player] wants to render displays. */
    private fun handleDisplayEnabled(player: Player, message: ByteArray) {
        runCatching {
            DataInputStream(ByteArrayInputStream(message)).use { input ->
                val enabled = input.readBoolean()
                PlayerManager.setDisplaysEnabled(player, enabled)
            }
        }.onFailure { e ->
            logger.warn("Failed to decode display enabled packet", e)
        }
    }

    /** Sends premium / admin / report flags, the in-world display batch, and stores [player]'s version. */
    private fun initializePlayer(player: Player, versionString: String) {
        PacketUtil.sendPremium(player, player.hasPermission(Main.config.permissions.premium))
        PacketUtil.sendIsAdmin(player, player.hasPermission(Main.config.permissions.delete))
        PacketUtil.sendReportEnabled(player, Main.config.settings.webhookUrl.isNotEmpty())
        sendAllDisplays(player)

        val version = parseVersionOrNull(versionString)
        PlayerManager.setVersion(player, version)
    }

    /** Compares [player]'s reported version against the cached mod / plugin versions and notifies once. */
    private fun checkForUpdates(player: Player, versionString: String) {
        val userVersion = parseVersionOrNull(versionString) ?: return

        // Check for mod update
        checkModUpdate(player, userVersion)

        // Check for plugin updates
        if (Main.config.settings.updatesEnabled &&
            player.hasPermission(Main.config.permissions.updates)
        ) {
            checkPluginUpdate(player)
        }
    }

    /** Tells [player] about a newer mod version if they haven't been notified this session. */
    private fun checkModUpdate(player: Player, userVersion: Semver) {
        val latestVersion = Main.modVersion ?: return

        if (userVersion < latestVersion && !PlayerManager.hasBeenNotifiedAboutModUpdate(player)) {
            sendModUpdateMessage(player, latestVersion)
            PlayerManager.setModUpdateNotified(player, true)
        }
    }

    /** Tells privileged [player] about a newer plugin release; skipped for `-SNAPSHOT` builds. */
    @Suppress("DEPRECATION")
    private fun checkPluginUpdate(player: Player) {
        val latestPluginVersion = Main.pluginLatestVersion ?: return

        if (PlayerManager.hasBeenNotifiedAboutPluginUpdate(player)) return

        val currentVersionString = plugin.description.version
        if (currentVersionString.contains("-SNAPSHOT", ignoreCase = true) ||
            currentVersionString.contains("-DEV", ignoreCase = true)) {
            return
        }

        val currentVersion = Semver.coerce(currentVersionString) ?: return
        val latestVersion = Semver.coerce(latestPluginVersion) ?: return

        if (currentVersion < latestVersion) {
            sendPluginUpdateMessage(player, latestPluginVersion)
            PlayerManager.setPluginUpdateNotified(player, true)
        }
    }

    /** Sends the localized `newVersion` message to [player], handling both plain and JSON templates. */
    private fun sendModUpdateMessage(player: Player, version: Semver) {
        val message = when (val rawMessage = Main.config.getMessageForPlayer(player, "newVersion")) {
            is String -> String.format(rawMessage, version.toString())
            else -> {
                val component = GsonComponentSerializer.gson()
                    .deserialize(gson.toJson(rawMessage))

                component.replaceText(
                    TextReplacementConfig.builder()
                        .matchLiteral("%s")
                        .replacement(version.toString())
                        .build()
                )
            }
        }
        MessageUtil.sendColoredMessage(player, message)
    }

    /** Sends the localized `newPluginVersion` message with the latest version interpolated in. */
    private fun sendPluginUpdateMessage(player: Player, version: String) {
        val template = Main.config.getMessageForPlayer(player, "newPluginVersion") as? String ?: return
        val message = String.format(template, version)
        MessageUtil.sendColoredMessage(player, message)
    }

    /** Streams every display in [player]'s world to them in small staggered batches. */
    private fun sendAllDisplays(player: Player) {
        val displays = DisplayManager.getDisplays()
            .filterIsInstance<PaperDisplayData>()
            .filter { it.pos1.world == player.world }
        if (displays.isEmpty()) return

        val batchSize = 5
        val batches = displays.chunked(batchSize)
        batches.forEachIndexed { index, batch ->
            val delayTicks = (index * 2).toLong()
            if (delayTicks == 0L) {
                sendDisplayBatch(player, batch)
            } else {
                Scheduler.runLater(delayTicks) {
                    if (player.isOnline) sendDisplayBatch(player, batch)
                }
            }
        }
    }

    /** Sends a single batch of `DisplayInfo` packets to [player]. */
    private fun sendDisplayBatch(player: Player, displays: List<PaperDisplayData>) {
        displays.forEach { display ->
            PacketUtil.sendDisplayInfo(
                listOf(player),
                display.id,
                display.ownerId,
                display.box.min,
                display.width,
                display.height,
                display.url,
                display.lang,
                display.facing,
                display.isSync,
                display.isLocked
            )
        }
    }

    /** Applies a client-supplied URL / language to a display, broadcasting, and resetting sync state. */
    private fun handleSetVideo(player: Player, message: ByteArray) {
        runCatching {
            DataInputStream(ByteArrayInputStream(message)).use { input ->
                val displayId = input.readUUID()
                val url = input.readString()
                val lang = input.readString()

                val displayData = DisplayManager.getDisplayData(displayId) as? PaperDisplayData
                    ?: return@runCatching

                if (displayData.isLocked &&
                    displayData.ownerId != player.uniqueId &&
                    !player.hasPermission(Main.config.permissions.delete)
                ) return@runCatching

                val wasSync = displayData.isSync
                displayData.url = url
                displayData.lang = lang

                val receivers = DisplayManager.getReceivers(displayData)
                DisplayManager.sendUpdate(displayData, receivers)
                if (wasSync) StateManager.resetAndBroadcast(displayData.id, receivers)
            }
        }.onFailure { e ->
            logger.warn("Failed to decode set_video packet", e)
        }
    }

    /** Updates the locked flag of a display owned by [player] and rebroadcasts. */
    private fun handleSetLocked(player: Player, message: ByteArray) {
        runCatching {
            DataInputStream(ByteArrayInputStream(message)).use { input ->
                val displayId = input.readUUID()
                val locked = input.readBoolean()

                val displayData = DisplayManager.getDisplayData(displayId) as? PaperDisplayData
                    ?: return@runCatching

                if (displayData.ownerId != player.uniqueId &&
                    !player.hasPermission(Main.config.permissions.delete)
                ) return@runCatching

                displayData.isLocked = locked

                val receivers = DisplayManager.getReceivers(displayData)
                DisplayManager.sendUpdate(displayData, receivers)
            }
        }.onFailure { e ->
            logger.warn("Failed to decode set_locked packet", e)
        }
    }

    /** Reads a single UUID payload, logging and returning null on decode failure. */
    private fun readUUIDPacket(message: ByteArray): UUID? {
        return runCatching {
            DataInputStream(ByteArrayInputStream(message)).use { input ->
                input.readUUID()
            }
        }.onFailure { e ->
            logger.error("Failed to decode UUID packet", e)
        }.getOrNull()
    }

    /** Reads a length-prefixed version string with strict bounds checks to reject malformed payloads. */
    private fun readVersionString(message: ByteArray): String {
        return DataInputStream(ByteArrayInputStream(message)).use { input ->
            val length = input.readVarInt()
            require(length in 1..maxVersionBytes) {
                "Invalid version packet length: $length."
            }
            require(length <= input.available()) {
                "Invalid version packet size: declared = $length, but available = ${input.available()}."
            }
            val data = ByteArray(length)
            input.readFully(data)
            String(data, 0, length)
        }
    }

    /** Sanitizes [raw] and coerces it into a [Semver], returning null if parsing fails. */
    private fun parseVersionOrNull(raw: String): Semver? {
        val sanitized = YouTubeUtil.sanitize(raw)?.takeIf { it.isNotEmpty() } ?: return null
        return Semver.coerce(sanitized)
    }

    /** Reads a 128-bit UUID using the shared encoding in [PacketUtil]. */
    private fun DataInputStream.readUUID() = PacketUtil.run { readUUID() }
    /** Reads a Minecraft-style VarInt using the shared encoding in [PacketUtil]. */
    private fun DataInputStream.readVarInt() = PacketUtil.run { readVarInt() }
    /** Reads a Minecraft-style VarLong using the shared encoding in [PacketUtil]. */
    private fun DataInputStream.readVarLong() = PacketUtil.run { readVarLong() }
    /** Reads a UTF-8 string prefixed by its byte length as a VarInt. */
    private fun DataInputStream.readString(): String {
        val length = readVarInt()
        val data = ByteArray(length)
        readFully(data)
        return String(data, Charsets.UTF_8)
    }
}

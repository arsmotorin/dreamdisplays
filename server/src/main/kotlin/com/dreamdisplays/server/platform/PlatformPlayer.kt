package com.dreamdisplays.server.platform

import com.dreamdisplays.server.utils.MessageUtil
import com.dreamdisplays.server.utils.net.ServerPacketHandler
import io.github.arsmotorin.ofrat.FabricOnly
import io.github.arsmotorin.ofrat.PaperOnly
import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.UUID

@PaperOnly val Player.platformUuid: UUID get() = uniqueId
@FabricOnly val ServerPlayer.platformUuid: UUID get() = uuid

@PaperOnly val Player.platformName: String get() = name
@FabricOnly val ServerPlayer.platformName: String get() = name.string

@PaperOnly fun CommandSender.platformPlayer(): Player? = this as? Player
@FabricOnly fun CommandSourceStack.platformPlayer(): ServerPlayer? = entity as? ServerPlayer

@PaperOnly fun CommandSender.findPlatformPlayer(name: String): Player? = Bukkit.getPlayerExact(name)
@FabricOnly fun CommandSourceStack.findPlatformPlayer(name: String): ServerPlayer? = server.playerList.getPlayerByName(name)

@PaperOnly fun CommandSender.canToggleOthers(): Boolean = hasPermission(paperToggleOthersPermission())
@FabricOnly fun CommandSourceStack.canToggleOthers(): Boolean = platformPlayer()?.let(ServerPacketHandler::isOpLevel2) ?: true

@PaperOnly fun CommandSender.sendPlatformFailure(message: String) {
    MessageUtil.sendColoredMessage(this, message)
}

@FabricOnly fun CommandSourceStack.sendPlatformFailure(message: String) {
    sendFailure(Component.literal(message))
}

@PaperOnly fun CommandSender.sendPlatformMessage(messageKey: String) {
    MessageUtil.sendMessage(this, messageKey)
}

@FabricOnly fun CommandSourceStack.sendPlatformMessage(messageKey: String) {
    platformPlayer()?.let { MessageUtil.sendMessage(it, messageKey) } ?: sendPlatformFailure(messageKey)
}

@PaperOnly fun Player.sendPlatformMessage(messageKey: String) {
    MessageUtil.sendMessage(this, messageKey)
}

@FabricOnly fun ServerPlayer.sendPlatformMessage(messageKey: String) {
    MessageUtil.sendMessage(this, messageKey)
}

@PaperOnly fun CommandSender.sendPlatformColoredMessage(message: Any?) {
    MessageUtil.sendColoredMessage(this, message)
}

@FabricOnly fun CommandSourceStack.sendPlatformColoredMessage(message: Any?) {
    platformPlayer()?.let { MessageUtil.sendColoredMessage(it, message) } ?: sendPlatformFailure(message?.toString() ?: "")
}

@PaperOnly fun Player.sendPlatformColoredMessage(message: Any?) {
    MessageUtil.sendColoredMessage(this, message)
}

@FabricOnly fun ServerPlayer.sendPlatformColoredMessage(message: Any?) {
    MessageUtil.sendColoredMessage(this, message)
}

@PaperOnly fun Player.sendDisplayEnabledPacket(enabled: Boolean) {
    val packetUtil = Class.forName("com.dreamdisplays.server.utils.net.PacketUtil").getField("INSTANCE").get(null)
    packetUtil.javaClass.getMethod("sendDisplayEnabled", Player::class.java, Boolean::class.javaPrimitiveType)
        .invoke(packetUtil, this, enabled)
}

@FabricOnly fun ServerPlayer.sendDisplayEnabledPacket(enabled: Boolean) {
    val packetUtil = Class.forName("com.dreamdisplays.server.utils.net.FabricPacketUtil").getField("INSTANCE").get(null)
    packetUtil.javaClass.getMethod("sendDisplayEnabled", ServerPlayer::class.java, Boolean::class.javaPrimitiveType)
        .invoke(packetUtil, this, enabled)
}

@PaperOnly fun CommandSender.formatMessage(key: String, vararg values: Any): String {
    return platformConfigMessage("com.dreamdisplays.server.Main", this as? Player, key, *values)
}

@FabricOnly fun CommandSourceStack.formatMessage(key: String, vararg values: Any): String {
    return platformConfigMessage("com.dreamdisplays.server.Server", platformPlayer(), key, *values)
}

@PaperOnly private fun paperToggleOthersPermission(): String {
    val config = platformConfig("com.dreamdisplays.server.Main")
    val permissions = config?.javaClass?.getMethod("getPermissions")?.invoke(config)
    return permissions?.javaClass?.getMethod("getToggleOthers")?.invoke(permissions) as? String
        ?: "dream-displays.toggle_others"
}

private fun platformConfigMessage(ownerClassName: String, player: Any?, key: String, vararg values: Any): String {
    val config = platformConfig(ownerClassName) ?: return key
    val template = config.javaClass.methods
        .firstOrNull { it.name == "getMessageForPlayer" && it.parameterTypes.size == 2 }
        ?.invoke(config, player, key) as? String ?: key
    return runCatching { String.format(template, *values) }.getOrElse { template }
}

private fun platformConfig(ownerClassName: String): Any? = runCatching {
    val companion = Class.forName(ownerClassName).getField("Companion").get(null)
    companion.javaClass.getMethod("getConfig").invoke(companion)
}.getOrNull()

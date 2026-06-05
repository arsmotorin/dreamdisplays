package com.dreamdisplays.server.commands.subcommands

import com.dreamdisplays.server.managers.PlayerManager
import com.dreamdisplays.server.platform.PlatformCommandSender
import com.dreamdisplays.server.platform.canToggleOthers
import com.dreamdisplays.server.platform.findPlatformPlayer
import com.dreamdisplays.server.platform.formatMessage
import com.dreamdisplays.server.platform.platformName
import com.dreamdisplays.server.platform.platformPlayer
import com.dreamdisplays.server.platform.platformUuid
import com.dreamdisplays.server.platform.sendDisplayEnabledPacket
import com.dreamdisplays.server.platform.sendPlatformColoredMessage
import com.dreamdisplays.server.platform.sendPlatformMessage

/**
 * Shared implementation for `/display on` and `/display off`.
 */
object ToggleDisplayCommand {
    fun execute(rawSender: Any, args: Array<String?>, enabled: Boolean): Boolean {
        val sender = rawSender as PlatformCommandSender
        val target = resolveTarget(sender, args) ?: return false
        val selfTarget = sender.platformPlayer()?.platformUuid == target.platformUuid

        if (!selfTarget && !sender.canToggleOthers()) {
            sender.sendPlatformMessage("displayCommandMissingPermission")
            return false
        }

        val alreadyKey = if (enabled) "display.already-enabled" else "display.already-disabled"
        val alreadyTargetKey = if (enabled) "display.already-enabled.target" else "display.already-disabled.target"
        val changedKey = if (enabled) "display.enabled" else "display.disabled"
        val changedTargetKey = if (enabled) "display.enabled.target" else "display.disabled.target"

        if (PlayerManager.isDisplaysEnabled(target) == enabled) {
            target.sendPlatformMessage(alreadyKey)
            if (!selfTarget) {
                sender.sendPlatformColoredMessage(sender.formatMessage(alreadyTargetKey, target.platformName))
            }
            return true
        }

        PlayerManager.setDisplaysEnabled(target, enabled)
        target.sendDisplayEnabledPacket(enabled)
        target.sendPlatformMessage(changedKey)
        if (!selfTarget) {
            sender.sendPlatformColoredMessage(sender.formatMessage(changedTargetKey, target.platformName))
        }
        return true
    }

    private fun resolveTarget(sender: PlatformCommandSender, args: Array<String?>) = when (args.size) {
        1 -> sender.platformPlayer() ?: run {
            sender.sendPlatformMessage("displayWrongCommand")
            null
        }
        2 -> {
            val targetName = args[1]?.trim().orEmpty()
            if (targetName.isBlank()) {
                sender.sendPlatformMessage("displayWrongCommand")
                null
            } else {
                sender.findPlatformPlayer(targetName) ?: run {
                    sender.sendPlatformColoredMessage(sender.formatMessage("displayTargetNotFound", targetName))
                    null
                }
            }
        }
        else -> {
            sender.sendPlatformMessage("displayWrongCommand")
            null
        }
    }
}

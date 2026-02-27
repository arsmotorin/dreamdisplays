package com.dreamdisplays.commands.subcommands

import com.dreamdisplays.Main
import com.dreamdisplays.managers.DisplayManager.getReceivers
import com.dreamdisplays.managers.DisplayManager.isContains
import com.dreamdisplays.managers.DisplayManager.sendUpdate
import com.dreamdisplays.utils.Message
import com.dreamdisplays.utils.YouTubeUtils
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.Locale

class VideoCommand : SubCommand {

    override val name = "video"
    override val permission = Main.config.permissions.video
    override val playerOnly = true

    override fun execute(sender: CommandSender, args: Array<String?>) {
        val player = (sender as? Player) ?: return
        if (args.size < 2) {
            Message.sendMessage(player, "invalidURL")
            return
        }

        val block = player.getTargetBlock(null, 32)
        val data = isContains(block.location)

        if (
            block.type != Main.config.settings.baseMaterial ||
            data == null ||
            data.ownerId != player.uniqueId
        ) {
            Message.sendMessage(player, "noDisplay")
            return
        }

        val code = YouTubeUtils.extractVideoIdFromUri(args[1] ?: "")
            ?: return Message.sendMessage(player, "invalidURL")

        data.apply {
            url = "https://youtube.com/watch?v=$code"
            lang = normalizeLangCode(args.getOrNull(2).orEmpty())
            isSync = false
        }

        sendUpdate(data, getReceivers(data))

        Message.sendMessage(player, "settedURL")
    }

    override fun complete(sender: CommandSender, args: Array<String?>): List<String> {
        if (args.size == 3) {
            return languageSuggestions
        }
        return emptyList()
    }

    private fun normalizeLangCode(raw: String): String {
        val base = raw.trim()
            .lowercase(Locale.ROOT)
            .replace('-', '_')
            .substringBefore('_')

        return when (base) {
            "ua" -> "uk"
            else -> base
        }
    }

    companion object {
        private val languageSuggestions: List<String> by lazy {
            val fromJavaLocales = Locale.getAvailableLocales()
                .asSequence()
                .map { it.language.lowercase(Locale.ROOT) }

            val fromPlugin = Main.config.languages.keys
                .asSequence()
                .map { it.trim().lowercase(Locale.ROOT).replace('-', '_').substringBefore('_') }

            return@lazy (fromJavaLocales + fromPlugin)
                .filter { it.matches(Regex("^[a-z]{2}$")) }
                .map { code ->
                    if (code == "uk") "ua" else code
                }
                .distinct()
                .sorted()
                .toList()
        }
    }
}

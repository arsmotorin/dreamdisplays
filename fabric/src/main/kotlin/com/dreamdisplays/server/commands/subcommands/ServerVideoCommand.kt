package com.dreamdisplays.server.commands.subcommands

import com.dreamdisplays.Server
import com.dreamdisplays.server.managers.DisplayManager
import com.dreamdisplays.server.utils.net.PacketUtil
import com.dreamdisplays.server.utils.net.ServerPacketHandler
import com.dreamdisplays.server.utils.MessageUtil
import com.dreamdisplays.server.utils.RegionUtil
import com.dreamdisplays.server.utils.YouTubeUtil
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import java.util.*

// TODO: for removing in stable 1.7.0
object ServerVideoCommand {
    fun execute(ctx: CommandContext<CommandSourceStack>, urlAndLang: String): Int {
        val player = ctx.source.entity as? ServerPlayer
            ?: return ctx.source.sendFailure(net.minecraft.network.chat.Component.literal("Players only.")).let { 0 }

        val parts = urlAndLang.trim().split(" ")
        val urlRaw = parts[0]
        val langRaw = if (parts.size > 1) parts.last() else ""

        if (urlRaw.isBlank()) {
            MessageUtil.sendMessage(player, "invalidURL")
            return 0
        }

        val code = YouTubeUtil.extractVideoIdFromUri(urlRaw)
            ?: return MessageUtil.sendMessage(player, "invalidURL").let { 0 }

        val config = Server.config
        val baseMaterialKey = config.settings.baseMaterial
        val baseMaterial = runCatching {
            BuiltInRegistries.BLOCK.get(Identifier.parse(baseMaterialKey))
        }.getOrNull()

        val targetPos = getTargetBlockPos(player)
            ?: return MessageUtil.sendMessage(player, "displayVideoWrongTargetBlock").let { 0 }

        if (baseMaterial != null) {
            val state = player.level().getBlockState(targetPos)
            if (state.block != baseMaterial) {
                MessageUtil.sendMessage(player, "displayVideoWrongTargetBlock")
                return 0
            }
        }

        val worldKey = RegionUtil.getLevelKey(player.level())
        val data = DisplayManager.isContains(worldKey, targetPos)
            ?: return MessageUtil.sendMessage(player, "noDisplay").let { 0 }

        if (data.ownerId != player.uuid && !ServerPacketHandler.isOpLevel2(player)) {
            MessageUtil.sendMessage(player, "displayVideoNotOwner")
            return 0
        }

        data.url = "https://youtube.com/watch?v=$code"
        data.lang = normalizeLangCode(langRaw)
        data.isSync = false

        val receivers = DisplayManager.getReceivers(data, ctx.source.server)
        PacketUtil.sendDisplayInfo(receivers, data)

        MessageUtil.sendMessage(player, "settedURL")
        return 1
    }

    private fun normalizeLangCode(raw: String): String {
        return when (val base = raw.trim().lowercase(Locale.ROOT).replace('-', '_').substringBefore('_')) {
            "ua" -> "uk"
            else -> base
        }
    }

    private fun getTargetBlockPos(player: ServerPlayer): net.minecraft.core.BlockPos? {
        val level = player.level()
        val start = player.eyePosition
        val end = start.add(player.lookAngle.scale(32.0))
        val hit = level.clip(ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player))
        return if (hit.type == HitResult.Type.BLOCK) hit.blockPos else null
    }
}

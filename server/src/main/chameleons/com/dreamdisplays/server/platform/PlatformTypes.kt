package com.dreamdisplays.server.platform

import io.github.arsmotorin.ofrat.Chameleon
import io.github.arsmotorin.ofrat.FabricOnly
import io.github.arsmotorin.ofrat.PaperOnly

@PaperOnly @Chameleon val PlatformPlayer: org.bukkit.entity.Player
@FabricOnly @Chameleon val PlatformPlayer: net.minecraft.server.level.ServerPlayer

@PaperOnly @Chameleon val PlatformCommandSender: org.bukkit.command.CommandSender
@FabricOnly @Chameleon val PlatformCommandSender: net.minecraft.commands.CommandSourceStack

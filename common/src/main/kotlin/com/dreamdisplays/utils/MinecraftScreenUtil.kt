package com.dreamdisplays.utils

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen

object MinecraftScreenUtil {
    fun currentScreen(mc: Minecraft): Screen? = mc.screen

    fun setScreen(mc: Minecraft, screen: Screen?) {
        mc.setScreen(screen)
    }
}

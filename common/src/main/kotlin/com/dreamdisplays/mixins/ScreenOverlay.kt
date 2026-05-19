package com.dreamdisplays.mixins

import com.dreamdisplays.client.ui.PipOverlayManager
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import org.lwjgl.glfw.GLFW
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Suppress("UNUSED")
@Mixin(Screen::class)
open class ScreenOverlay {
    @Inject(
        method = ["extractRenderStateWithTooltipAndSubtitles"],
        at = [At("RETURN")]
    )
    open fun onRenderReturn(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float, ci: CallbackInfo) {
        if (PipOverlayManager.isEmpty) return
        val mc = Minecraft.getInstance()
        if (mc.level == null || mc.player == null) return
        val leftPressed = GLFW.glfwGetMouseButton(mc.window.handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS
        PipOverlayManager.renderAll(mc, graphics, mouseX, mouseY, leftPressed, partialTick)
    }
}

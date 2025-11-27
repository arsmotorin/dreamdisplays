package com.dreamdisplays.screen.widgets

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.WidgetSprites
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.util.ARGB
import org.jspecify.annotations.NullMarked
import kotlin.math.max

// TODO: rewrite this
@NullMarked
abstract class Button(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    private val iconWidth: Int,
    private val iconHeight: Int,
    private var iconTexture: Identifier,
    private val margin: Int
) : AbstractWidget(x, y, width, height, Component.empty()) {
    private var setTextures: WidgetSprites? = null

    fun setIconTexture(iconTexture: Identifier) {
        this.iconTexture = iconTexture
    }

    fun setTextures(setTextures: WidgetSprites) {
        this.setTextures = setTextures
    }

    abstract fun onPress()

    override fun onClick(event: MouseButtonEvent, doubleClick: Boolean) {
        this.onPress()
    }

    override fun onRelease(event: MouseButtonEvent) {
        super.playDownSound(Minecraft.getInstance().soundManager)
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) {
    }

    override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        graphics.blitSprite(
            RenderPipelines.GUI_TEXTURED,
            if (setTextures != null) setTextures!!.get(
                this.active,
                this.isHoveredOrFocused
            ) else TEXTURES.get(this.active, this.isHoveredOrFocused),
            this.x,
            this.y,
            this.getWidth(),
            this.getHeight(),
            ARGB.white(this.alpha)
        )

        val dW = getWidth() - 2 * margin
        val dH = getHeight() - 2 * margin

        var iconW = dW
        val iconH = max((iconHeight.toDouble()) / iconWidth * iconW, dH.toDouble()).toInt()
        iconW = ((iconWidth.toDouble()) / iconHeight * iconH).toInt()

        val dx = x + getWidth() / 2 - iconW / 2
        val dy = y + getHeight() / 2 - iconH / 2

        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, iconTexture, dx, dy, iconW, iconH, ARGB.white(this.alpha))
    }

    companion object {
        private val TEXTURES = WidgetSprites(
            Identifier.withDefaultNamespace("widget/button"),
            Identifier.withDefaultNamespace("widget/button_disabled"),
            Identifier.withDefaultNamespace("widget/button_highlighted")
        )
    }
}

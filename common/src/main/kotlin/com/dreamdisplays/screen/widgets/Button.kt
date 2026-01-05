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

/**
 * A button widget that we use in display configuration GUI.
 */
@NullMarked
abstract class Button(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    private val iconWidth: Int,
    private val iconHeight: Int,
    private var iconTextureId: Identifier,
    private val margin: Int
) : AbstractWidget(x, y, width, height, Component.empty()) {

    private var setSprites: WidgetSprites? = null

    fun setIconTextureId(iconTextureId: Identifier) {
        this.iconTextureId = iconTextureId
    }

    fun setSprites(setSprites: WidgetSprites?) {
        this.setSprites = setSprites
    }

    abstract fun onPress()

    override fun onClick(event: MouseButtonEvent, doubleClick: Boolean) {
        this.onPress()
        super.playDownSound(Minecraft.getInstance().soundManager)
    }

    override fun updateWidgetNarration(builder: NarrationElementOutput) {
    }

    override fun renderWidget(
        guiGraphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float
    ) {
        guiGraphics.blitSprite(
            RenderPipelines.GUI_TEXTURED,
            if (setSprites != null) setSprites!![this.active, this.isHoveredOrFocused] else SPRITES[this.active, this.isHoveredOrFocused],
            this.x,
            this.y,
            this.width,
            this.height,
            ARGB.white(this.alpha)
        )

        val dW = width - 2 * margin
        val dH = height - 2 * margin

        var iconW = dW
        val iconH = maxOf(
            (iconHeight.toDouble() / iconWidth * iconW).toInt(),
            dH
        )
        iconW = (iconWidth.toDouble() / iconHeight * iconH).toInt()

        val dx = x + width / 2 - iconW / 2
        val dy = y + height / 2 - iconH / 2

        guiGraphics.blitSprite(
            RenderPipelines.GUI_TEXTURED,
            iconTextureId,
            dx,
            dy,
            iconW,
            iconH,
            ARGB.white(this.alpha)
        )
    }

    companion object {
        private val SPRITES = WidgetSprites(
            Identifier.withDefaultNamespace("widget/button"),
            Identifier.withDefaultNamespace("widget/button_disabled"),
            Identifier.withDefaultNamespace("widget/button_highlighted")
        )
    }
}

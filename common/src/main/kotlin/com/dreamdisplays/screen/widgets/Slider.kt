package com.dreamdisplays.screen.widgets

import com.mojang.blaze3d.platform.InputConstants
import com.mojang.blaze3d.platform.InputConstants.KEY_LEFT
import com.mojang.blaze3d.platform.InputConstants.KEY_RIGHT
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.GuiGraphics.HoveredTextEffects
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarratedElementType
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Component.translatable
import net.minecraft.network.chat.MutableComponent
import net.minecraft.resources.Identifier
import net.minecraft.resources.Identifier.withDefaultNamespace
import net.minecraft.util.Mth
import net.minecraft.util.Mth.clamp
import org.jspecify.annotations.NullMarked

/**
 * A button widget that we use in display configuration GUI.
 */
@NullMarked
abstract class Slider(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    message: Component,
    @JvmField var value: Double,
) : AbstractWidget(x, y, width, height, message) {

    private var sliderFocused: Boolean = false

    private val texture: Identifier
        get() = if (this.isFocused && !this.sliderFocused) HIGHLIGHTED_TEXTURE_ID else TEXTURE_ID

    private val handleTexture: Identifier
        get() = if (!this.isHovered && !this.sliderFocused) HANDLE_TEXTURE_ID else HANDLE_HIGHLIGHTED_TEXTURE_ID

    override fun createNarrationMessage(): MutableComponent {
        return translatable("gui.narrate.slider", this.message)
    }

    override fun updateWidgetNarration(builder: NarrationElementOutput) {
        builder.add(NarratedElementType.TITLE, this.createNarrationMessage())
        if (this.active) {
            if (this.isFocused) {
                builder.add(
                    NarratedElementType.USAGE,
                    translatable("narration.slider.usage.focused")
                )
            } else {
                builder.add(
                    NarratedElementType.USAGE,
                    translatable("narration.slider.usage.hovered")
                )
            }
        }
    }

    // from ExtendedSlider.class
    override fun renderWidget(
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
    ) {
        graphics.blitSprite(
            GUI_TEXTURED,
            this.texture,
            this.x,
            this.y,
            this.width,
            this.height
        )
        graphics.blitSprite(
            GUI_TEXTURED,
            this.handleTexture,
            this.x + (this.value * (this.width - 8).toDouble()).toInt(),
            this.y,
            8,
            this.height
        )
        val i = if (this.active) 16777215 else 10526880
        val message = this.message
            .copy()
            .withStyle { style -> style.withColor(i) }
        this.renderScrollingStringOverContents(
            graphics.textRendererForWidget(
                this,
                HoveredTextEffects.TOOLTIP_AND_CURSOR
            ),
            message,
            2
        )
    }

    override fun onClick(event: MouseButtonEvent, doubleClick: Boolean) {
        this.setValueFromMouse(event.x)
    }

    override fun onDrag(
        event: MouseButtonEvent,
        dragX: Double,
        dragY: Double,
    ) {
        this.setValueFromMouse(event.x)
        super.onDrag(event, dragX, dragY)
    }

    private fun setValueFromMouse(mouseX: Double) {
        this.setValue((mouseX - (this.x + 4)) / (this.width - 8))
    }

    private fun setValue(value: Double) {
        val d = this.value
        this.value = clamp(value, 0.0, 1.0)
        if (d != this.value) {
            this.applyValue()
        }

        this.updateMessage()
    }

    protected abstract fun updateMessage()

    protected abstract fun applyValue()

    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.key == KEY_LEFT || event.key == KEY_RIGHT) {
            val f = if (event.key == KEY_LEFT) -1.0f else 1.0f
            this.setValue(this.value + (f / (this.width - 8)).toDouble())
            return true
        }
        return super.keyPressed(event)
    }

    companion object {
        private val TEXTURE_ID = withDefaultNamespace(
            "widget/slider"
        )
        private val HIGHLIGHTED_TEXTURE_ID =
            withDefaultNamespace("widget/slider_highlighted")
        private val HANDLE_TEXTURE_ID =
            withDefaultNamespace("widget/slider_handle")
        private val HANDLE_HIGHLIGHTED_TEXTURE_ID =
            withDefaultNamespace("widget/slider_handle_highlighted")
    }
}

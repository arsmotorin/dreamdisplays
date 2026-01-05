package com.dreamdisplays.screen.widgets

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.resources.Identifier
import org.jspecify.annotations.NullMarked

/**
 * A button widget that we use in display configuration GUI.
 */
@NullMarked
abstract class Toggle(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    message: Component,
    @JvmField var value: Boolean
) : AbstractWidget(x, y, width, height, message) {

    private var dValue: Double = if (value) 1.0 else 0.0
    private var sliderFocused: Boolean = false

    private val texture: Identifier
        get() = if (this.isFocused && !this.sliderFocused) HIGHLIGHTED_TEXTURE_ID else TEXTURE_ID

    private val handleTexture: Identifier
        get() = if (!this.isHovered && !this.sliderFocused) HANDLE_TEXTURE_ID else HANDLE_HIGHLIGHTED_TEXTURE_ID

    override fun createNarrationMessage(): MutableComponent {
        return Component.translatable("gui.narrate.slider", this.message)
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) {
    }

    override fun renderWidget(
        guiGraphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float
    ) {
        guiGraphics.blitSprite(
            RenderPipelines.GUI_TEXTURED,
            this.texture,
            this.x,
            this.y,
            this.width,
            this.height
        )
        guiGraphics.blitSprite(
            RenderPipelines.GUI_TEXTURED,
            this.handleTexture,
            this.x + (this.dValue * (this.width - 8).toDouble()).toInt(),
            this.y,
            8,
            this.height
        )
        val i = if (this.active) 16777215 else 10526880
        val message = this.message
            .copy()
            .withStyle { style -> style.withColor(i) }
        this.renderScrollingStringOverContents(
            guiGraphics.textRendererForWidget(
                this,
                GuiGraphics.HoveredTextEffects.TOOLTIP_AND_CURSOR
            ),
            message,
            2
        )
    }

    override fun onClick(event: MouseButtonEvent, doubleClick: Boolean) {
        this.setValue(!this.value)
    }

    fun setValue(value: Boolean) {
        if (this.value != value) {
            this.value = value
            this.dValue = if (this.value) 1.0 else 0.0
            this.applyValue()
        }

        this.updateMessage()
    }

    protected abstract fun updateMessage()

    protected abstract fun applyValue()

    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.key == InputConstants.KEY_LEFT || event.key == InputConstants.KEY_RIGHT) {
            this.setValue(!this.value)
            return true
        }
        return super.keyPressed(event)
    }

    companion object {
        private val TEXTURE_ID = Identifier.withDefaultNamespace(
            "widget/slider"
        )
        private val HIGHLIGHTED_TEXTURE_ID =
            Identifier.withDefaultNamespace("widget/slider_highlighted")
        private val HANDLE_TEXTURE_ID =
            Identifier.withDefaultNamespace("widget/slider_handle")
        private val HANDLE_HIGHLIGHTED_TEXTURE_ID =
            Identifier.withDefaultNamespace("widget/slider_handle_highlighted")
    }
}

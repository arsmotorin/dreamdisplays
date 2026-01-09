package com.dreamdisplays.screen.settings.widgets

import net.minecraft.client.InputType
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
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
    var value: Boolean,
) : AbstractWidget(x, y, width, height, message) {
    private var dValue: Double
    private var sliderFocused = false

    init {
        this.dValue = (if (value) 1 else 0).toDouble()
    }

    private val texture: Identifier
        get() = if (this.isFocused && !this.sliderFocused)
            HIGHLIGHTED_TEXTURE_ID
        else
            TEXTURE_ID

    private val handleTexture: Identifier
        get() = if (!this.isHovered && !this.sliderFocused)
            HANDLE_TEXTURE_ID
        else
            HANDLE_HIGHLIGHTED_TEXTURE_ID

    override fun createNarrationMessage(): MutableComponent {
        return Component.translatable("gui.narrate.slider", this.getMessage())
    }

    public override fun updateWidgetNarration(output: NarrationElementOutput) {
    }

    public override fun renderWidget(
        guiGraphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
    ) {
        guiGraphics.blitSprite(
            RenderPipelines.GUI_TEXTURED,
            this.texture,
            this.x,
            this.y,
            this.getWidth(),
            this.getHeight()
        )
        guiGraphics.blitSprite(
            RenderPipelines.GUI_TEXTURED,
            this.handleTexture,
            this.x + (this.dValue * (this.width - 8).toDouble()).toInt(),
            this.y,
            8,
            this.getHeight()
        )
        val i = if (this.active) 16777215 else 10526880
        val message = this.getMessage()
            .copy()
            .withStyle({ style: Style? -> style!!.withColor(i) })
        this.renderScrollingStringOverContents(
            guiGraphics.textRendererForWidget(
                this,
                GuiGraphics.HoveredTextEffects.TOOLTIP_AND_CURSOR
            ),
            message,
            2
        ) // , i | Mth.ceil(this.alpha * 255.0F) << 24
    }

    override fun setFocused(focused: Boolean) {
        super.setFocused(focused)
        if (!focused) {
            this.sliderFocused = false
        } else {
            val guiNavigationType =
                Minecraft.getInstance().lastInputType
            if (guiNavigationType == InputType.MOUSE ||
                guiNavigationType == InputType.KEYBOARD_TAB
            ) {
                this.sliderFocused = true
            }
        }
    }

    private fun setValueFromMouse() {
        value = !value
        dValue = (if (value) 1 else 0).toDouble()
    }

    override fun onClick(event: MouseButtonEvent, doubleClick: Boolean) {
        this.setValueFromMouse()
        this.updateMessage()
        this.applyValue()
        super.playDownSound(Minecraft.getInstance().soundManager)
    }

    protected abstract fun updateMessage()

    abstract fun applyValue()

    fun setToggleValue(newValue: Boolean) {
        if (this.value != newValue) {
            this.value = newValue
            this.dValue = (if (newValue) 1 else 0).toDouble()
            updateMessage()
            applyValue()
        }
    }

    companion object {
        private val TEXTURE_ID = Identifier.withDefaultNamespace(
            "widget/slider"
        )
        private val HIGHLIGHTED_TEXTURE_ID = Identifier.withDefaultNamespace("widget/slider_highlighted")
        private val HANDLE_TEXTURE_ID = Identifier.withDefaultNamespace("widget/slider_handle")
        private val HANDLE_HIGHLIGHTED_TEXTURE_ID = Identifier.withDefaultNamespace("widget/slider_handle_highlighted")
    }
}

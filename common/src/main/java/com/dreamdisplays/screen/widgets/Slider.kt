package com.dreamdisplays.screen.widgets

import net.minecraft.client.InputType
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarratedElementType
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.resources.Identifier
import net.minecraft.util.Mth
import org.jspecify.annotations.NullMarked

// TODO: rewrite this
@NullMarked
abstract class Slider(x: Int, y: Int, width: Int, height: Int, message: Component, value: Double) :
    AbstractWidget(x, y, width, height, message) {
    @JvmField
    @Suppress("CanBePrimaryConstructorProperty")
    var value: Double = value
    private var sliderFocused = false

    private val texture: Identifier
        get() = if (this.isFocused && !this.sliderFocused) HIGHLIGHTED_TEXTURE else TEXTURE

    private val handleTexture: Identifier
        get() = if (!this.isHovered && !this.sliderFocused) HANDLE_TEXTURE else HANDLE_HIGHLIGHTED_TEXTURE

    override fun createNarrationMessage(): MutableComponent {
        return Component.translatable("gui.narrate.slider", this.getMessage())
    }

    public override fun updateWidgetNarration(builder: NarrationElementOutput) {
        builder.add(NarratedElementType.TITLE, this.createNarrationMessage())
        if (this.active) {
            if (this.isFocused) {
                builder.add(NarratedElementType.USAGE, Component.translatable("narration.slider.usage.focused"))
            } else {
                builder.add(NarratedElementType.USAGE, Component.translatable("narration.slider.usage.hovered"))
            }
        }
    }

    // from ExtendedSlider.class
    public override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        graphics.blitSprite(
            RenderPipelines.GUI_TEXTURED,
            this.texture, this.x, this.y, this.getWidth(), this.getHeight()
        )
        graphics.blitSprite(
            RenderPipelines.GUI_TEXTURED,
            this.handleTexture,
            this.x + (this.value * (this.width - 8).toDouble()).toInt(),
            this.y,
            8,
            this.getHeight()
        )
        val i = if (this.active) 16777215 else 10526880
        val message = this.getMessage().copy().withStyle { style: Style? -> style!!.withColor(i) }
        this.renderScrollingStringOverContents(
            graphics.textRendererForWidget(
                this,
                GuiGraphics.HoveredTextEffects.TOOLTIP_AND_CURSOR
            ), message, 2
        ) // , i | Mth.ceil(this.alpha * 255.0F) << 24
    }

    override fun onClick(event: MouseButtonEvent, doubleClick: Boolean) {
        super.onClick(event, doubleClick)
        this.setValueFromMouse(event.x())
    }

    override fun onRelease(event: MouseButtonEvent) {
        super.playDownSound(Minecraft.getInstance().soundManager)
    }

    override fun onDrag(event: MouseButtonEvent, dragX: Double, dragY: Double) {
        super.onDrag(event, dragX, dragY)
        this.setValueFromMouse(event.x())
    }

    override fun setFocused(focused: Boolean) {
        super.setFocused(focused)
        if (!focused) {
            this.sliderFocused = false
        } else {
            val guiNavigationType = Minecraft.getInstance().lastInputType
            if (guiNavigationType == InputType.MOUSE || guiNavigationType == InputType.KEYBOARD_TAB) {
                this.sliderFocused = true
            }
        }
    }

    private fun setValueFromMouse(mouseX: Double) {
        this.setFractionalValue((mouseX - (this.x + 4).toDouble()) / (this.width - 8).toDouble())
    }

    private fun setFractionalValue(fractionalValue: Double) {
        val oldValue = this.value
        this.value = Mth.clamp(fractionalValue, 0.0, 1.0)
        if (!Mth.equal(oldValue, this.value)) {
            this.applyValue()
        }
        this.updateMessage()
    }

    protected abstract fun updateMessage()

    protected abstract fun applyValue()

    companion object {
        private val TEXTURE = Identifier.withDefaultNamespace("widget/slider")
        private val HIGHLIGHTED_TEXTURE = Identifier.withDefaultNamespace("widget/slider_highlighted")
        private val HANDLE_TEXTURE = Identifier.withDefaultNamespace("widget/slider_handle")
        private val HANDLE_HIGHLIGHTED_TEXTURE = Identifier.withDefaultNamespace("widget/slider_handle_highlighted")
    }
}

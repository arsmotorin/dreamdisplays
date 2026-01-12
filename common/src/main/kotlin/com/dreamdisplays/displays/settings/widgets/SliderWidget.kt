package com.dreamdisplays.displays.settings.widgets

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

/**
 * A button widget that we use in display configuration GUI.
 */
// TODO: wtf rewrite this shit
@NullMarked
abstract class SliderWidget(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    message: Component,
    var value: Double,
) : AbstractWidget(x, y, width, height, message) {
    private var sliderFocused = false

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

    public override fun updateWidgetNarration(builder: NarrationElementOutput) {
        builder.add(NarratedElementType.TITLE, this.createNarrationMessage())
        if (this.active) {
            if (this.isFocused) {
                builder.add(
                    NarratedElementType.USAGE,
                    Component.translatable("narration.slider.usage.focused")
                )
            } else {
                builder.add(
                    NarratedElementType.USAGE,
                    Component.translatable("narration.slider.usage.hovered")
                )
            }
        }
    }

    // from ExtendedSlider.class
    public override fun renderWidget(
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
    ) {
        graphics.blitSprite(
            RenderPipelines.GUI_TEXTURED,
            this.texture,
            this.getX(),
            this.getY(),
            this.getWidth(),
            this.getHeight()
        )
        graphics.blitSprite(
            RenderPipelines.GUI_TEXTURED,
            this.handleTexture,
            this.getX() + (this.value * (this.width - 8).toDouble()).toInt(),
            this.getY(),
            8,
            this.getHeight()
        )
        val i = if (this.active) 16777215 else 10526880
        val message = this.getMessage()
            .copy()
            .withStyle { style: Style? -> style!!.withColor(i) }
        this.renderScrollingStringOverContents(
            graphics.textRendererForWidget(
                this,
                GuiGraphics.HoveredTextEffects.TOOLTIP_AND_CURSOR
            ),
            message,
            2
        )
    }

    override fun onClick(event: MouseButtonEvent, doubleClick: Boolean) {
        this.setValueFromMouse(event.x())
        super.playDownSound(Minecraft.getInstance().getSoundManager())
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
            val guiNavigationType =
                Minecraft.getInstance().getLastInputType()
            if (guiNavigationType == InputType.MOUSE ||
                guiNavigationType == InputType.KEYBOARD_TAB
            ) {
                this.sliderFocused = true
            }
        }
    }

    private fun setValueFromMouse(mouseX: Double) {
        this.setFractionalValue(
            (mouseX - (this.getX() + 4).toDouble()) / (this.width - 8).toDouble()
        )
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
        private val TEXTURE_ID = Identifier.withDefaultNamespace(
            "widget/slider"
        )
        private val HIGHLIGHTED_TEXTURE_ID = Identifier.withDefaultNamespace("widget/slider_highlighted")
        private val HANDLE_TEXTURE_ID = Identifier.withDefaultNamespace("widget/slider_handle")
        private val HANDLE_HIGHLIGHTED_TEXTURE_ID = Identifier.withDefaultNamespace("widget/slider_handle_highlighted")
    }
}

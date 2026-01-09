package com.dreamdisplays.screen.settings.widgets

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
import java.util.function.LongSupplier

/**
 * A timeline slider widget for video playback position control.
 */
@NullMarked
abstract class Timeline(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    private val currentTimeSupplier: LongSupplier,
    private val durationSupplier: LongSupplier,
) : AbstractWidget(x, y, width, height, Component.empty()) {
    private var sliderFocused = false

    private val texture: Identifier
        get() = if (this.isFocused() && !this.sliderFocused)
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

    private val currentValue: Double
        get() {
            val duration = durationSupplier.asLong
            if (duration <= 0) return 0.0
            val currentTime = currentTimeSupplier.asLong
            return Mth.clamp(currentTime.toDouble() / duration, 0.0, 1.0)
        }

    public override fun renderWidget(
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
    ) {
        val value = this.currentValue

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
            this.getX() + (value * (this.width - 8).toDouble()).toInt(),
            this.getY(),
            8,
            this.getHeight()
        )

        // Render time text
        val currentNanos = currentTimeSupplier.asLong
        val durationNanos = durationSupplier.asLong
        val timeText = formatTime(currentNanos) + " / " + formatTime(durationNanos)
        val message: Component = Component.literal(timeText)

        val textColor = if (this.active) 16777215 else 10526880
        val styledMessage = message.copy()
            .withStyle({ style: Style? -> style!!.withColor(textColor) })
        this.renderScrollingStringOverContents(
            graphics.textRendererForWidget(
                this,
                GuiGraphics.HoveredTextEffects.TOOLTIP_AND_CURSOR
            ),
            styledMessage,
            2
        )
    }

    private fun formatTime(nanos: Long): String {
        val totalSeconds = nanos / 1000000000L
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            return String.format("%d:%02d", minutes, seconds)
        }
    }

    override fun onClick(event: MouseButtonEvent, doubleClick: Boolean) {
        this.setValueFromMouse(event.x())
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
            val guiNavigationType =
                Minecraft.getInstance().lastInputType
            if (guiNavigationType == InputType.MOUSE ||
                guiNavigationType == InputType.KEYBOARD_TAB
            ) {
                this.sliderFocused = true
            }
        }
    }

    private fun setValueFromMouse(mouseX: Double) {
        var fractionalValue = (mouseX - (this.x + 4).toDouble()) / (this.width - 8).toDouble()
        fractionalValue = Mth.clamp(fractionalValue, 0.0, 1.0)

        val duration = durationSupplier.asLong
        if (duration > 0) {
            val targetNanos = (fractionalValue * duration).toLong()
            onSeek(targetNanos)
        }
    }

    /**
     * Called when user seeks to a specific position.
     * @param nanos target position in nanoseconds
     */
    protected abstract fun onSeek(nanos: Long)

    companion object {
        private val TEXTURE_ID = Identifier.withDefaultNamespace(
            "widget/slider"
        )
        private val HIGHLIGHTED_TEXTURE_ID = Identifier.withDefaultNamespace("widget/slider_highlighted")
        private val HANDLE_TEXTURE_ID = Identifier.withDefaultNamespace("widget/slider_handle")
        private val HANDLE_HIGHLIGHTED_TEXTURE_ID = Identifier.withDefaultNamespace("widget/slider_handle_highlighted")
    }
}

package com.dreamdisplays.screen.widgets;

import net.minecraft.client.InputType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.jspecify.annotations.NullMarked;

import java.util.function.LongSupplier;

/**
 * A timeline slider widget for video playback position control.
 */
@NullMarked
public abstract class Timeline extends AbstractWidget {

    private static final Identifier TEXTURE_ID = Identifier.withDefaultNamespace(
            "widget/slider"
    );
    private static final Identifier HIGHLIGHTED_TEXTURE_ID =
            Identifier.withDefaultNamespace("widget/slider_highlighted");
    private static final Identifier HANDLE_TEXTURE_ID =
            Identifier.withDefaultNamespace("widget/slider_handle");
    private static final Identifier HANDLE_HIGHLIGHTED_TEXTURE_ID =
            Identifier.withDefaultNamespace("widget/slider_handle_highlighted");

    private final LongSupplier currentTimeSupplier;
    private final LongSupplier durationSupplier;
    private boolean sliderFocused;

    public Timeline(
            int x,
            int y,
            int width,
            int height,
            LongSupplier currentTimeSupplier,
            LongSupplier durationSupplier
    ) {
        super(x, y, width, height, Component.empty());
        this.currentTimeSupplier = currentTimeSupplier;
        this.durationSupplier = durationSupplier;
    }

    private Identifier getTexture() {
        return this.isFocused() && !this.sliderFocused
                ? HIGHLIGHTED_TEXTURE_ID
                : TEXTURE_ID;
    }

    private Identifier getHandleTexture() {
        return !this.isHovered && !this.sliderFocused
                ? HANDLE_TEXTURE_ID
                : HANDLE_HIGHLIGHTED_TEXTURE_ID;
    }

    protected MutableComponent createNarrationMessage() {
        return Component.translatable("gui.narrate.slider", this.getMessage());
    }

    public void updateWidgetNarration(NarrationElementOutput builder) {
        builder.add(NarratedElementType.TITLE, this.createNarrationMessage());
        if (this.active) {
            if (this.isFocused()) {
                builder.add(
                        NarratedElementType.USAGE,
                        Component.translatable("narration.slider.usage.focused")
                );
            } else {
                builder.add(
                        NarratedElementType.USAGE,
                        Component.translatable("narration.slider.usage.hovered")
                );
            }
        }
    }

    private double getCurrentValue() {
        long duration = durationSupplier.getAsLong();
        if (duration <= 0) return 0;
        long currentTime = currentTimeSupplier.getAsLong();
        return Mth.clamp((double) currentTime / duration, 0.0, 1.0);
    }

    @Override
    public void renderWidget(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        double value = getCurrentValue();

        graphics.blitSprite(
                RenderPipelines.GUI_TEXTURED,
                this.getTexture(),
                this.getX(),
                this.getY(),
                this.getWidth(),
                this.getHeight()
        );
        graphics.blitSprite(
                RenderPipelines.GUI_TEXTURED,
                this.getHandleTexture(),
                this.getX() + (int) (value * (double) (this.width - 8)),
                this.getY(),
                8,
                this.getHeight()
        );

        // Render time text
        long currentNanos = currentTimeSupplier.getAsLong();
        long durationNanos = durationSupplier.getAsLong();
        String timeText = formatTime(currentNanos) + " / " + formatTime(durationNanos);
        Component message = Component.literal(timeText);

        int textColor = this.active ? 16777215 : 10526880;
        MutableComponent styledMessage = message.copy()
                .withStyle(style -> style.withColor(textColor));
        this.renderScrollingStringOverContents(
                graphics.textRendererForWidget(
                        this,
                        GuiGraphics.HoveredTextEffects.TOOLTIP_AND_CURSOR
                ),
                styledMessage,
                2
        );
    }

    private String formatTime(long nanos) {
        long totalSeconds = nanos / 1_000_000_000L;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%d:%02d", minutes, seconds);
        }
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        this.setValueFromMouse(event.x());
        super.playDownSound(Minecraft.getInstance().getSoundManager());
    }

    @Override
    protected void onDrag(MouseButtonEvent event, double dragX, double dragY) {
        super.onDrag(event, dragX, dragY);
        this.setValueFromMouse(event.x());
    }


    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (!focused) {
            this.sliderFocused = false;
        } else {
            InputType guiNavigationType =
                    Minecraft.getInstance().getLastInputType();
            if (
                    guiNavigationType == InputType.MOUSE ||
                            guiNavigationType == InputType.KEYBOARD_TAB
            ) {
                this.sliderFocused = true;
            }
        }
    }

    private void setValueFromMouse(double mouseX) {
        double fractionalValue = (mouseX - (double) (this.getX() + 4)) / (double) (this.width - 8);
        fractionalValue = Mth.clamp(fractionalValue, 0.0, 1.0);

        long duration = durationSupplier.getAsLong();
        if (duration > 0) {
            long targetNanos = (long) (fractionalValue * duration);
            onSeek(targetNanos);
        }
    }

    /**
     * Called when user seeks to a specific position.
     * @param nanos target position in nanoseconds
     */
    protected abstract void onSeek(long nanos);
}


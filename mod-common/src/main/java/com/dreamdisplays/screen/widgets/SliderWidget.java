package com.dreamdisplays.screen.widgets;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.minecraft.client.InputType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public abstract class SliderWidget extends AbstractWidget {
    private static final @NonNull Identifier TEXTURE = Identifier.withDefaultNamespace("widget/slider");
    private static final @NonNull Identifier HIGHLIGHTED_TEXTURE = Identifier.withDefaultNamespace("widget/slider_highlighted");
    private static final @NonNull Identifier HANDLE_TEXTURE = Identifier.withDefaultNamespace("widget/slider_handle");
    private static final @NonNull Identifier HANDLE_HIGHLIGHTED_TEXTURE = Identifier.withDefaultNamespace("widget/slider_handle_highlighted");
    public double value;
    private boolean sliderFocused;

    public SliderWidget(int x, int y, int width, int height, @NonNull Component text, double value) {
        super(x, y, width, height, text);
        this.value = value;
    }

    private Identifier getTexture() {
        return this.isFocused() && !this.sliderFocused ? HIGHLIGHTED_TEXTURE : TEXTURE;
    }

    private Identifier getHandleTexture() {
        return !this.isHovered && !this.sliderFocused ? HANDLE_TEXTURE : HANDLE_HIGHLIGHTED_TEXTURE;
    }

    protected @NonNull MutableComponent createNarrationMessage() {
        return Component.translatable("gui.narrate.slider", this.getMessage());
    }

    public void updateWidgetNarration(NarrationElementOutput builder) {
        builder.add(NarratedElementType.TITLE, this.createNarrationMessage());
        if (this.active) {
            if (this.isFocused()) {
                builder.add(NarratedElementType.USAGE, Component.translatable("narration.slider.usage.focused"));
            } else {
                builder.add(NarratedElementType.USAGE, Component.translatable("narration.slider.usage.hovered"));
            }
        }

    }

    // from ExtendedSlider.class
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, this.getTexture(), this.getX(), this.getY(), this.getWidth(), this.getHeight());
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, this.getHandleTexture(), this.getX() + (int)(this.value * (double)(this.width - 8)), this.getY(), 8, this.getHeight());
        int i = this.active ? 16777215 : 10526880;
        MutableComponent message = this.getMessage().copy().withStyle((style) -> style.withColor(i));
        this.renderScrollingStringOverContents(graphics.textRendererForWidget(this, GuiGraphics.HoveredTextEffects.TOOLTIP_ONLY), message, 2); // , i | Mth.ceil(this.alpha * 255.0F) << 24
    }

	public void onClick(double mouseX, double mouseY) {
        this.setValueFromMouse(mouseX);
    }

    protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
        this.setValueFromMouse(mouseX);
    }

    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (!focused) {
            this.sliderFocused = false;
        } else {
            InputType guiNavigationType = Minecraft.getInstance().getLastInputType();
            if (guiNavigationType == InputType.MOUSE || guiNavigationType == InputType.KEYBOARD_TAB) {
                this.sliderFocused = true;
            }

        }
    }

    private void setValueFromMouse(double mouseX) {
        this.setValue((mouseX - (double)(this.getX() + 4)) / (double)(this.width - 8));
    }

    private void setValue(double value) {
        double d = this.value;
        this.value = Mth.clamp(value, 0.0, 1.0);
        if (d != this.value) {
            this.applyValue();
        }

        this.updateMessage();
    }

    @Override
    public void playDownSound(@NonNull SoundManager soundManager) {
    }

    protected abstract void updateMessage();

    protected abstract void applyValue();
}

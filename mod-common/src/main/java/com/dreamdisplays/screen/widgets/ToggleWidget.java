package com.dreamdisplays.screen.widgets;

import net.minecraft.client.InputType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

@NullMarked
public abstract class ToggleWidget extends AbstractWidget {
	private static final Identifier TEXTURE = Identifier.withDefaultNamespace("widget/slider");
	private static final Identifier HIGHLIGHTED_TEXTURE = Identifier.withDefaultNamespace("widget/slider_highlighted");
	private static final Identifier HANDLE_TEXTURE = Identifier.withDefaultNamespace("widget/slider_handle");
	private static final Identifier HANDLE_HIGHLIGHTED_TEXTURE = Identifier.withDefaultNamespace("widget/slider_handle_highlighted");
	private double dValue;
	public boolean value;
	private boolean sliderFocused;

	public ToggleWidget(int x, int y, int width, int height, Component text, boolean value) {
		super(x, y, width, height, text);
		this.dValue = value ? 1 : 0;
		this.value = value;
	}

	private Identifier getTexture() {
		return this.isFocused() && !this.sliderFocused ? HIGHLIGHTED_TEXTURE : TEXTURE;
	}

	private Identifier getHandleTexture() {
		return !this.isHovered && !this.sliderFocused ? HANDLE_TEXTURE : HANDLE_HIGHLIGHTED_TEXTURE;
	}

	@Override
	protected MutableComponent createNarrationMessage() {
		return Component.translatable("gui.narrate.slider", this.getMessage());
	}

	@Override
	public void updateWidgetNarration(NarrationElementOutput builder) {

	}

	@Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, this.getTexture(), this.getX(), this.getY(), this.getWidth(), this.getHeight());
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, this.getHandleTexture(), this.getX() + (int)(this.dValue * (double)(this.width - 8)), this.getY(), 8, this.getHeight());
        int i = this.active ? 16777215 : 10526880;
        MutableComponent message = this.getMessage().copy().withStyle((style) -> style.withColor(i));
        this.renderScrollingStringOverContents(graphics.textRendererForWidget(this, GuiGraphics.HoveredTextEffects.TOOLTIP_ONLY), message, 2); // , i | Mth.ceil(this.alpha * 255.0F) << 24
    }

	@Override
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

	private void setValueFromMouse() {
		value = !value;
		dValue = value ? 1 : 0;
	}

	public void onClick(double mouseX, double mouseY) {
		this.playDownSound(Minecraft.getInstance().getSoundManager());
		this.setValueFromMouse();
		this.updateMessage();
		this.applyValue();
	}

	@Override
	public void playDownSound(SoundManager soundManager) {
	}

	protected abstract void updateMessage();

	protected abstract void applyValue();
}

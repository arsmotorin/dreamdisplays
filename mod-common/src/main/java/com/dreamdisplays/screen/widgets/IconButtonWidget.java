package com.dreamdisplays.screen.widgets;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public abstract class IconButtonWidget extends AbstractWidget {
	private final int iw;
    private final int ih;
	private final int margin;

	private @NonNull Identifier iconTexture;

	public void setIconTexture(@NonNull Identifier iconTexture) {
		this.iconTexture = iconTexture;
	}

	private static final @NonNull WidgetSprites TEXTURES = new WidgetSprites(
		Identifier.withDefaultNamespace("widget/button"), Identifier.withDefaultNamespace("widget/button_disabled"), Identifier.withDefaultNamespace("widget/button_highlighted")
	);
	
	private @Nullable WidgetSprites settedTextures = null;

	public IconButtonWidget(int i, int j, int k, int l, int iw, int ih, @NonNull Identifier iconTexture, int margin) {
		super(i, j, k, l, Component.empty());

		this.iw = iw;
		this.ih = ih;
		this.iconTexture = iconTexture;
		this.margin = margin;
	}

	public void setTextures(@NonNull WidgetSprites settedTextures) {
		this.settedTextures = settedTextures;
	}

	public abstract void onPress();

	public void onClick(double mouseX, double mouseY) {
		this.playDownSound(net.minecraft.client.Minecraft.getInstance().getSoundManager());
		this.onPress();
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput builder) {}

	@Override
	protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, settedTextures != null ? settedTextures.get(this.active, this.isHoveredOrFocused()) : TEXTURES.get(this.active, this.isHoveredOrFocused()), this.getX(), this.getY(), this.getWidth(), this.getHeight(), ARGB.white(this.alpha));

		int dW = getWidth() - 2*margin;
		int dH = getHeight() - 2*margin;

		int iconW = dW;
		int iconH = (int) Math.max(((double) ih)/iw * iconW, dH);
		iconW = (int) (((double)iw)/ih * iconH);

		int dx = getX() + getWidth()/2-iconW/2;
		int dy = getY() + getHeight()/2-iconH/2;

		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, iconTexture, dx, dy, iconW, iconH, ARGB.white(this.alpha));

	}
}

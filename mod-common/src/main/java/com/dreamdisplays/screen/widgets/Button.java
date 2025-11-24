package com.dreamdisplays.screen.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public abstract class Button extends AbstractWidget {
    private static final WidgetSprites TEXTURES = new WidgetSprites(
        Identifier.withDefaultNamespace("widget/button"), Identifier.withDefaultNamespace("widget/button_disabled"), Identifier.withDefaultNamespace("widget/button_highlighted")
    );
    private final int iconWidth;
    private final int iconHeight;
    private final int margin;
    private Identifier iconTexture;
    private @Nullable WidgetSprites setTextures = null;

    public Button(int x, int y, int width, int height, int iconWidth, int iconHeight, Identifier iconTexture, int margin) {
        super(x, y, width, height, Component.empty());
        this.iconWidth = iconWidth;
        this.iconHeight = iconHeight;
        this.iconTexture = iconTexture;
        this.margin = margin;
    }

    public void setIconTexture(Identifier iconTexture) {
        this.iconTexture = iconTexture;
    }

    public void setTextures(WidgetSprites setTextures) {
        this.setTextures = setTextures;
    }

    public abstract void onPress();

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        this.onPress();
    }

    @Override
    public void onRelease(MouseButtonEvent event) {
        super.playDownSound(Minecraft.getInstance().getSoundManager());
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, setTextures != null ? setTextures.get(this.active, this.isHoveredOrFocused()) : TEXTURES.get(this.active, this.isHoveredOrFocused()), this.getX(), this.getY(), this.getWidth(), this.getHeight(), ARGB.white(this.alpha));

        int dW = getWidth() - 2 * margin;
        int dH = getHeight() - 2 * margin;

        int iconW = dW;
        int iconH = (int) Math.max(((double) iconHeight) / iconWidth * iconW, dH);
        iconW = (int) (((double) iconWidth) / iconHeight * iconH);

        int dx = getX() + getWidth() / 2 - iconW / 2;
        int dy = getY() + getHeight() / 2 - iconH / 2;

        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, iconTexture, dx, dy, iconW, iconH, ARGB.white(this.alpha));

    }
}

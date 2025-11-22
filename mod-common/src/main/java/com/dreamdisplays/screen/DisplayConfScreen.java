package com.dreamdisplays.screen;

import com.dreamdisplays.PlatformlessInitializer;
import com.dreamdisplays.net.DeletePacket;
import com.dreamdisplays.net.ReportPacket;
import com.dreamdisplays.render.RenderUtil2D;
import com.dreamdisplays.screen.widgets.IconButtonWidget;
import com.dreamdisplays.screen.widgets.SliderWidget;
import com.dreamdisplays.screen.widgets.ToggleWidget;

import java.util.List;
import java.util.Objects;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

// Configuration screen for Dream Displays with volume, render distance, quality, and sync settings
public class DisplayConfScreen extends Screen {

    SliderWidget volume = null;
    SliderWidget renderD = null;
    SliderWidget quality = null;
    ToggleWidget sync = null;

    IconButtonWidget backButton = null;
    IconButtonWidget forwardButton = null;
    IconButtonWidget pauseButton = null;

    IconButtonWidget renderDReset = null;
    IconButtonWidget qualityReset = null;
    IconButtonWidget syncReset = null;

    IconButtonWidget deleteButton = null;
    IconButtonWidget reportButton = null;

    public com.dreamdisplays.screen.Screen screen;

    protected DisplayConfScreen() {
        super(Component.translatable("dreamdisplays.ui.title"));
    }


    @Override
    protected void init() {
        volume = new SliderWidget(0, 0, 0, 0, Component.literal((int) Math.floor(screen.getVolume() * 100) + "%"), screen.getVolume()) {
            @Override
            protected void updateMessage() {
                setMessage(Component.literal((int) Math.floor(value * 100) + "%"));
            }

            @Override
            protected void applyValue() {
                screen.setVolume((float) value);
            }
        };

        backButton = new IconButtonWidget(0, 0, 0, 0, 64, 64, ResourceLocation.fromNamespaceAndPath(PlatformlessInitializer.MOD_ID, "bbi"), 2) {
            @Override
            public void onPress() {
                screen.seekBackward();
            }
        };

        forwardButton = new IconButtonWidget(0, 0, 0, 0, 64, 64, ResourceLocation.fromNamespaceAndPath(PlatformlessInitializer.MOD_ID, "bfi"), 2) {
            @Override
            public void onPress() {
                screen.seekForward();
            }
        };

        pauseButton = new IconButtonWidget(0, 0, 0, 0, 64, 64, ResourceLocation.fromNamespaceAndPath(PlatformlessInitializer.MOD_ID, "bpi"), 2) {
            @Override
            public void onPress() {
                screen.setPaused(!screen.getPaused());
                setIconTexture(screen.getPaused() ? ResourceLocation.fromNamespaceAndPath(PlatformlessInitializer.MOD_ID, "bupi") : ResourceLocation.fromNamespaceAndPath(PlatformlessInitializer.MOD_ID, "bpi"));
            }
        };

        pauseButton.setIconTexture(screen.getPaused() ? ResourceLocation.fromNamespaceAndPath(PlatformlessInitializer.MOD_ID, "bupi") : ResourceLocation.fromNamespaceAndPath(PlatformlessInitializer.MOD_ID, "bpi"));

        renderD = new SliderWidget(0, 0, 0, 0, Component.nullToEmpty(String.valueOf(PlatformlessInitializer.config.defaultDistance)), (PlatformlessInitializer.config.defaultDistance-24)/(96-24)) {
            @Override
            protected void updateMessage() {
                setMessage(Component.nullToEmpty(String.valueOf((int) (value*(96-24)) + 24)));
            }

            @Override
            protected void applyValue() {
                PlatformlessInitializer.config.defaultDistance = (int) (value * (96-24) + 24);
            }
        };

        quality = new SliderWidget(0, 0, 0, 0, Component.nullToEmpty(screen.getQuality()+"p"), ((double) fromQuality(screen.getQuality())) / screen.getQualityList().size()) {
            @Override
            protected void updateMessage() {
                setMessage(Component.nullToEmpty(toQuality((int) (value*screen.getQualityList().size()))+"p"));
            }

            @Override
            protected void applyValue() {
                screen.setQuality(toQuality((int) (value * screen.getQualityList().size())));
            }
        };

        renderDReset = new IconButtonWidget(0, 0, 0, 0, 64, 64, ResourceLocation.fromNamespaceAndPath(PlatformlessInitializer.MOD_ID, "bri"), 2) {
            @Override
            public void onPress() {
                PlatformlessInitializer.config.defaultDistance = 64;
                renderD.value = 64;
                renderD.setMessage(Component.nullToEmpty("64"));
            }
        };

        qualityReset = new IconButtonWidget(0, 0, 0, 0, 64, 64, ResourceLocation.fromNamespaceAndPath(PlatformlessInitializer.MOD_ID, "bri"), 2) {
            @Override
            public void onPress() {
                screen.setQuality(toQuality(fromQuality("720")).replace("p", ""));
                quality.value = (double) 2 /screen.getQualityList().size();
                quality.setMessage(Component.nullToEmpty(toQuality(2) + "p"));
            }
        };

        sync = new ToggleWidget(0, 0, 0, 0, Component.translatable(screen.isSync ? "dreamdisplays.button.enabled" : "dreamdisplays.button.disabled"), screen.isSync) {
            @Override
            protected void updateMessage() {
                setMessage(Component.translatable(screen.isSync ? "dreamdisplays.button.enabled" : "dreamdisplays.button.disabled"));
            }

            @Override
            protected void applyValue() {
                if (screen.owner) {
                    screen.isSync = value;
                    syncReset.active = value;
                    screen.waitForMFInit(() -> screen.sendSync());
                }
            }
        };

        syncReset = new IconButtonWidget(0, 0, 0, 0, 64, 64, ResourceLocation.fromNamespaceAndPath(PlatformlessInitializer.MOD_ID, "bri"), 2) {
            @Override
            public void onPress() {
                if (screen.owner) {
                    sync.value = false;
                    screen.waitForMFInit(() -> screen.sendSync());
                }
            }
        };

        sync.active = screen.owner;

        deleteButton = new IconButtonWidget(0, 0, 0, 0, 64, 64, ResourceLocation.fromNamespaceAndPath(PlatformlessInitializer.MOD_ID, "delete"), 2) {
            @Override
            public void onPress() {
                PlatformlessInitializer.sendPacket(new DeletePacket(screen.getID()));
                onClose();
            }
        };

        deleteButton.active = screen.owner;

        reportButton = new IconButtonWidget(0, 0, 0, 0, 64, 64, ResourceLocation.fromNamespaceAndPath(PlatformlessInitializer.MOD_ID, "report"), 2) {
            @Override
            public void onPress() {
                PlatformlessInitializer.sendPacket(new ReportPacket(screen.getID()));
                onClose();
            }
        };

        WidgetSprites textures = new WidgetSprites(ResourceLocation.fromNamespaceAndPath(PlatformlessInitializer.MOD_ID, "widgets/red_button"), ResourceLocation.fromNamespaceAndPath(PlatformlessInitializer.MOD_ID, "widgets/red_button_disabled"), ResourceLocation.fromNamespaceAndPath(PlatformlessInitializer.MOD_ID, "widgets/red_button_highlighted"));

        deleteButton.setTextures(textures);
        reportButton.setTextures(textures);

        addRenderableWidget(volume);
        addRenderableWidget(backButton);
        addRenderableWidget(forwardButton);
        addRenderableWidget(pauseButton);
        addRenderableWidget(renderD);
        addRenderableWidget(quality);
        addRenderableWidget(qualityReset);
        addRenderableWidget(renderDReset);
        addRenderableWidget(sync);
        addRenderableWidget(syncReset);
        addRenderableWidget(deleteButton);
        addRenderableWidget(reportButton);
    }

    private void renderTooltipIfHovered(GuiGraphics guiGraphics, int mouseX, int mouseY,
                                        int elementX, int elementY, int elementWidth, int elementHeight,
                                        List<Component> tooltip) {
        if (mouseX >= elementX && mouseX <= elementX + elementWidth &&
                mouseY >= elementY && mouseY <= elementY + elementHeight) {
            guiGraphics.setComponentTooltipForNextFrame(Minecraft.getInstance().font, tooltip, mouseX, mouseY);
        }
    }

    // Renders the display configuration screen
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        super.render(guiGraphics, mouseX, mouseY, delta);
        Component headerText = Component.translatable("dreamdisplays.ui.title");

        int vCH = 25;

        deleteButton.setX(10);
        deleteButton.setY(this.height - vCH - 10);
        deleteButton.setHeight(vCH);
        deleteButton.setWidth(vCH);

        reportButton.setX(this.width - vCH - 10);
        reportButton.setY(this.height - vCH - 10);
        reportButton.setHeight(vCH);
        reportButton.setWidth(vCH);


        if (screen.errored) {
            volume.active = false;
            renderD.active = false;
            quality.active = false;
            sync.active = false;
            backButton.active = false;
            forwardButton.active = false;
            pauseButton.active = false;
            renderDReset.active = false;
            qualityReset.active = false;
            syncReset.active = false;

            List<Component> errorText = List.of(
                    Component.translatable("dreamdisplays.error.loadingerror.1").withStyle(style -> style.withColor(0xff0000)),
                    Component.translatable("dreamdisplays.error.loadingerror.2").withStyle(style -> style.withColor(0xff0000)),
                    Component.translatable("dreamdisplays.error.loadingerror.3").withStyle(style -> style.withColor(0xff0000)),
                    Component.translatable("dreamdisplays.error.loadingerror.4").withStyle(style -> style.withColor(0xff0000)),
                    Component.translatable("dreamdisplays.error.loadingerror.5").withStyle(style -> style.withColor(0xff0000))
            );

            int yP = (int) ((double) this.height / 2 - ((double) (font.lineHeight + 2) * errorText.size()) / 2);

            int mW = 0;
            for (Component text : errorText) {
                mW = Math.max(font.width(text), mW);
            }

            for (Component text : errorText) {
                guiGraphics.drawString(font, text, this.width / 2 - font.width(text) / 2, yP += 2 + font.lineHeight, 0xFFFFFF, true);
            }

            deleteButton.render(guiGraphics, mouseX, mouseY, delta);
            reportButton.render(guiGraphics, mouseX, mouseY, delta);

            return;
        }

        syncReset.active = screen.owner && screen.isSync;
        renderDReset.active = PlatformlessInitializer.config.defaultDistance != 64;
        qualityReset.active = !Objects.equals(screen.getQuality(), "720");

        int headerTextWidth = font.width(headerText);
        int headerTextX = (this.width - headerTextWidth) / 2;
        int headerTextY = 15;
        guiGraphics.drawString(font, headerText, headerTextX, headerTextY, 0xFFFFFF, true);

        int maxSW = this.width / 3;

        // Screen dimensions
        int sW = maxSW;
        int sH = (int) Math.min((int) (screen.getHeight() / screen.getWidth() * sW), this.height / 3.5);
        sW = (int) (screen.getWidth() / screen.getHeight() * sH);
        int sX = this.width / 2 - sW / 2;
        int cY = font.lineHeight + 15 * 2;

        guiGraphics.fill(this.width / 2 - maxSW / 2, cY, this.width / 2 + maxSW / 2, cY + sH, 0xff000000);
        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate(0, 0); // see if the 10 offset is necessary, pls let it not be
        renderScreen(guiGraphics, sX, cY, sW, sH);
        guiGraphics.pose().popMatrix();

        cY += sH;
        cY += 5;

        // Settings for volume, backButton, forwardButton, pauseButton
        volume.setX(this.width / 2 - maxSW / 2);
        volume.setY(cY);
        volume.setHeight(vCH);
        volume.setWidth(Math.min(maxSW / 3, maxSW / 2 - vCH * 9 / 8 - 5));

        backButton.setX(this.width / 2 - vCH * 9 / 8);
        backButton.setY(cY);
        backButton.setHeight(vCH);
        backButton.setWidth(vCH);

        forwardButton.setX(this.width / 2 + vCH / 8);
        forwardButton.setY(cY);
        forwardButton.setHeight(vCH);
        forwardButton.setWidth(vCH);

        pauseButton.setX(this.width / 2 + maxSW / 2 - vCH);
        pauseButton.setY(cY);
        pauseButton.setHeight(vCH);
        pauseButton.setWidth(vCH);

        backButton.active = !(screen.isSync && !screen.owner);
        forwardButton.active = !(screen.isSync && !screen.owner);
        pauseButton.active = !(screen.isSync && !screen.owner);

        sync.active = (screen.owner);
        deleteButton.active = (screen.owner);

        cY += 10 + vCH;

        // Volume, backButton, forwardButton, pauseButton
        placeButton(vCH, maxSW, cY, renderD, renderDReset);

        // Tooltip for Render Distance
        Component renderDText = Component.translatable("dreamdisplays.button.render-distance");
        int renderDTextX = this.width / 2 - maxSW / 2;
        int renderDTextY = cY + vCH / 2 - font.lineHeight / 2;
        guiGraphics.drawString(font, renderDText, renderDTextX, renderDTextY, 0xFFFFFF, true);

        // Tooltip
        List<Component> renderDTooltip = List.of(
                Component.translatable("dreamdisplays.button.render-distance.tooltip.1").withStyle(style -> style.withColor(ChatFormatting.WHITE).withBold(true)),
                Component.translatable("dreamdisplays.button.render-distance.tooltip.2").withStyle(style -> style.withColor(ChatFormatting.GRAY)),
                Component.translatable("dreamdisplays.button.render-distance.tooltip.3").withStyle(style -> style.withColor(ChatFormatting.GRAY)),
                Component.translatable("dreamdisplays.button.render-distance.tooltip.4"),
                Component.translatable("dreamdisplays.button.render-distance.tooltip.5").withStyle(style -> style.withColor(ChatFormatting.DARK_GRAY)),
                Component.translatable("dreamdisplays.button.render-distance.tooltip.6").withStyle(style -> style.withColor(ChatFormatting.DARK_GRAY)),
                Component.translatable("dreamdisplays.button.render-distance.tooltip.7"),
                Component.translatable("dreamdisplays.button.render-distance.tooltip.8", PlatformlessInitializer.config.defaultDistance).withStyle(style -> style.withColor(ChatFormatting.GOLD))
        );

        cY += 5 + vCH;

        // quality and qualityReset settings
        placeButton(vCH, maxSW, cY, quality, qualityReset);

        // Setting the quality text and calculating coordinates for tooltip
        Component qualityText = Component.translatable("dreamdisplays.button.quality");
        int qualityTextX = this.width / 2 - maxSW / 2;
        int qualityTextY = cY + vCH / 2 - font.lineHeight / 2;
        guiGraphics.drawString(font, qualityText, qualityTextX, qualityTextY, 0xFFFFFF, true);

        // Tooltip
        List<Component> qualityTooltip = new java.util.ArrayList<>(List.of(
                Component.translatable("dreamdisplays.button.quality.tooltip.1").withStyle(style -> style.withColor(ChatFormatting.WHITE).withBold(true)),
                Component.translatable("dreamdisplays.button.quality.tooltip.2").withStyle(style -> style.withColor(ChatFormatting.GRAY)),
                Component.translatable("dreamdisplays.button.quality.tooltip.3"),
                Component.translatable("dreamdisplays.button.quality.tooltip.4", screen.getQuality()).withStyle(style -> style.withColor(ChatFormatting.GOLD))
        ));

        // Add warning if quality is 1080p or higher
        if (Integer.parseInt(screen.getQuality()) >= 1080) {
            qualityTooltip.add(Component.translatable("dreamdisplays.button.quality.tooltip.5").withStyle(style -> style.withColor(ChatFormatting.YELLOW)));
        }

        cY += 15 + vCH;
        placeButton(vCH, maxSW, cY, sync, syncReset);

        // Setting the sync text and calculating coordinates for the tooltip
        Component syncText = Component.translatable("dreamdisplays.button.synchronization");
        int syncTextX = this.width / 2 - maxSW / 2;
        int syncTextY = cY + vCH / 2 - font.lineHeight / 2;
        guiGraphics.drawString(font, syncText, syncTextX, syncTextY, 0xFFFFFF, true);

        List<Component> syncTooltip = List.of(
                Component.translatable("dreamdisplays.button.synchronization.tooltip.1").withStyle(style -> style.withColor(ChatFormatting.WHITE).withBold(true)),
                Component.translatable("dreamdisplays.button.synchronization.tooltip.2").withStyle(style -> style.withColor(ChatFormatting.GRAY)),
                Component.translatable("dreamdisplays.button.synchronization.tooltip.3").withStyle(style -> style.withColor(ChatFormatting.GRAY)),
                Component.translatable("dreamdisplays.button.synchronization.tooltip.4"),
                Component.translatable("dreamdisplays.button.synchronization.tooltip.5", sync.value ? Component.translatable("dreamdisplays.button.enabled") : Component.translatable("dreamdisplays.button.disabled")).withStyle(style -> style.withColor(ChatFormatting.GOLD))
        );

        renderTooltipIfHovered(guiGraphics, mouseX, mouseY, renderDTextX, renderDTextY,
                font.width(renderDText), font.lineHeight, renderDTooltip);
        renderTooltipIfHovered(guiGraphics, mouseX, mouseY, qualityTextX, qualityTextY,
                font.width(qualityText), font.lineHeight, qualityTooltip);
        renderTooltipIfHovered(guiGraphics, mouseX, mouseY, syncTextX, syncTextY,
                font.width(syncText), font.lineHeight, syncTooltip);

        // Tooltips for delete and report buttons
        List<Component> deleteTooltip = List.of(
                Component.translatable("dreamdisplays.button.delete.tooltip.1").withStyle(style -> style.withColor(ChatFormatting.WHITE).withBold(true)),
                Component.translatable("dreamdisplays.button.delete.tooltip.2").withStyle(style -> style.withColor(ChatFormatting.GRAY))
        );

        List<Component> reportTooltip = List.of(
                Component.translatable("dreamdisplays.button.report.tooltip.1").withStyle(style -> style.withColor(ChatFormatting.WHITE).withBold(true)),
                Component.translatable("dreamdisplays.button.report.tooltip.2").withStyle(style -> style.withColor(ChatFormatting.GRAY))
        );

        renderTooltipIfHovered(guiGraphics, mouseX, mouseY, deleteButton.getX(), deleteButton.getY(),
                deleteButton.getWidth(), deleteButton.getHeight(), deleteTooltip);
        renderTooltipIfHovered(guiGraphics, mouseX, mouseY, reportButton.getX(), reportButton.getY(),
                reportButton.getWidth(), reportButton.getHeight(), reportTooltip);

        // Render all child elements (buttons, sliders, etc.)
        for (GuiEventListener child : children()) {
            if (child instanceof Renderable drawable) {
                drawable.render(guiGraphics, mouseX, mouseY, delta);
            }
        }
    }

    // Places button and its reset button at specified coordinates
    private void placeButton(int vCH, int maxSW, int cY, AbstractWidget renderD, IconButtonWidget renderDReset) {
        renderD.setX(this.width / 2 + maxSW / 2 - 80 - vCH - 5);
        renderD.setY(cY);
        renderD.setHeight(vCH);
        renderD.setWidth(80);

        renderDReset.setX(this.width / 2 + maxSW / 2 - vCH);
        renderDReset.setY(cY);
        renderDReset.setHeight(vCH);
        renderDReset.setWidth(vCH);
    }

    // Renders display screen preview
    private void renderScreen(GuiGraphics guiGraphics, int x, int y, int w, int h) {
        if (screen.isVideoStarted()) {
            RenderUtil2D.drawTexturedQuad(guiGraphics.pose(), screen.texture.getTextureView(), x, y, w, h, screen.renderLayer);
        } else if (screen.hasPreviewTexture()) {
            RenderUtil2D.drawTexturedQuad(guiGraphics.pose(), screen.getPreviewTexture().getTextureView(), x, y, w, h, screen.previewRenderLayer);
        } else {
            guiGraphics.fill(x, y, x + w, y + h, 0xFF000000);
        }
    }

    // Opens the display configuration screen
    public static void open(com.dreamdisplays.screen.Screen screen) {
        DisplayConfScreen displayConfScreen = new DisplayConfScreen();
        displayConfScreen.setScreen(screen);
        Minecraft.getInstance().setScreen(displayConfScreen);
    }

    // Converts resolution index to quality string
    private String toQuality(int resolution) {
        List<Integer> list = screen.getQualityList();

        if (list.isEmpty()) return "144";

        int i = Math.max(Math.min(resolution, list.size() - 1), 0);
        return list.get(i).toString();
    }

    // Converts quality string to resolution index
    private int fromQuality(String quality) {
        List<Integer> list = screen.getQualityList();

        if (list.isEmpty()) return 0;
        int cQ = Integer.parseInt(quality.replace("p", ""));

        int res = list.stream().filter(q -> q==cQ).findAny().orElse(Math.max(Math.min(list.getLast(), cQ), list.getFirst()));
        return list.indexOf(list.contains(res) ? res: list.getFirst());
    }

    // Sets the screen for the display config screen
    private void setScreen(com.dreamdisplays.screen.Screen screen) {
        this.screen = screen;
    }
}

package com.dreamdisplays.screen;

import com.dreamdisplays.Initializer;
import com.dreamdisplays.net.Delete;
import com.dreamdisplays.net.Report;
import com.dreamdisplays.render.Render2D;
import com.dreamdisplays.screen.widgets.Button;
import com.dreamdisplays.screen.widgets.Slider;
import com.dreamdisplays.screen.widgets.Toggle;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

// TODO: rewrite Menu class because it's really messy
// Maybe we need a separate classes for error screen, normal config screen, etc.

// Menu for configuring of displays
@NullMarked
public class Menu extends Screen {

    // UI components
    @Nullable Slider volume = null;
    @Nullable Slider renderD = null;
    @Nullable Slider quality = null;
    @Nullable Toggle sync = null;

    @Nullable Button backButton = null;
    @Nullable Button forwardButton = null;
    @Nullable Button pauseButton = null;

    @Nullable Button renderDReset = null;
    @Nullable Button qualityReset = null;
    @Nullable Button syncReset = null;

    @Nullable Button deleteButton = null;
    @Nullable Button reportButton = null;

    // Duplication of original video screen
    com.dreamdisplays.screen.@Nullable Screen screen = null;

    protected Menu() {
        super(Component.translatable("dreamdisplays.ui.title"));
    }

    // Opens the display configuration screen
    public static void open(com.dreamdisplays.screen.Screen screen) {
        Menu displayConfScreen = new Menu();
        displayConfScreen.setScreen(screen);
        Minecraft.getInstance().setScreen(displayConfScreen);
    }

    @Override
    protected void init() {

        if (screen != null)
            volume = new Slider(0, 0, 0, 0, Component.literal((int) Math.floor(screen.getVolume() * 100) + "%"), screen.getVolume()) {
                @Override
                protected void updateMessage() {
                    setMessage(Component.literal((int) Math.floor(value * 100) + "%"));
                }

                @Override
                protected void applyValue() {
                    screen.setVolume((float) value);
                }
            };

        backButton = new Button(0, 0, 0, 0, 64, 64, Identifier.fromNamespaceAndPath(Initializer.MOD_ID, "bbi"), 2) {
            @Override
            public void onPress() {
                screen.seekBackward();
            }
        };

        forwardButton = new Button(0, 0, 0, 0, 64, 64, Identifier.fromNamespaceAndPath(Initializer.MOD_ID, "bfi"), 2) {
            @Override
            public void onPress() {
                screen.seekForward();
            }
        };

        pauseButton = new Button(0, 0, 0, 0, 64, 64, Identifier.fromNamespaceAndPath(Initializer.MOD_ID, "bpi"), 2) {
            @Override
            public void onPress() {
                screen.setPaused(!screen.getPaused());
                setIconTexture(screen.getPaused() ? Identifier.fromNamespaceAndPath(Initializer.MOD_ID, "bupi") : Identifier.fromNamespaceAndPath(Initializer.MOD_ID, "bpi"));
            }
        };

        pauseButton.setIconTexture(screen.getPaused() ? Identifier.fromNamespaceAndPath(Initializer.MOD_ID, "bupi") : Identifier.fromNamespaceAndPath(Initializer.MOD_ID, "bpi"));

        renderD = new Slider(0, 0, 0, 0, Component.nullToEmpty(String.valueOf(Initializer.config.defaultDistance)), (Initializer.config.defaultDistance - 24) / (double) (96 - 24)) {
            @Override
            protected void updateMessage() {
                setMessage(Component.nullToEmpty(String.valueOf((int) (value * (96 - 24)) + 24)));
            }

            @Override
            protected void applyValue() {
                Initializer.config.defaultDistance = (int) (value * (96 - 24) + 24);
                Initializer.config.save();
                Initializer.config.reload();
            }
        };

        quality = new Slider(0, 0, 0, 0, Component.nullToEmpty(screen.getQuality() + "p"), ((double) fromQuality(screen.getQuality())) / screen.getQualityList().size()) {
            @Override
            protected void updateMessage() {
                setMessage(Component.nullToEmpty(toQuality((int) (value * screen.getQualityList().size())) + "p"));
            }

            @Override
            protected void applyValue() {
                screen.setQuality(toQuality((int) (value * screen.getQualityList().size())));
            }
        };

        renderDReset = new Button(0, 0, 0, 0, 64, 64, Identifier.fromNamespaceAndPath(Initializer.MOD_ID, "bri"), 2) {
            @Override
            public void onPress() {
                Initializer.config.defaultDistance = 64;
                Initializer.config.save();
                Initializer.config.reload();
                renderD.value = (64 - 24) / (double) (96 - 24);
                renderD.setMessage(Component.nullToEmpty("64"));
            }
        };

        qualityReset = new Button(0, 0, 0, 0, 64, 64, Identifier.fromNamespaceAndPath(Initializer.MOD_ID, "bri"), 2) {
            @Override
            public void onPress() {
                int targetIndex = fromQuality("720");
                screen.setQuality(toQuality(targetIndex).replace("p", ""));
                quality.value = (double) targetIndex / screen.getQualityList().size();
                quality.setMessage(Component.nullToEmpty(toQuality(targetIndex) + "p"));
            }
        };

        sync = new Toggle(0, 0, 0, 0, Component.translatable(screen.isSync ? "dreamdisplays.button.enabled" : "dreamdisplays.button.disabled"), screen.isSync) {
            @Override
            protected void updateMessage() {
                setMessage(Component.translatable(value ? "dreamdisplays.button.enabled" : "dreamdisplays.button.disabled"));
            }

            @Override
            public void applyValue() {
                if (screen.owner && syncReset != null) {
                    screen.isSync = value;
                    syncReset.active = !value;
                    screen.waitForMFInit(() -> screen.sendSync());
                }
            }
        };

        // TODO: fix sync reset button not updating correctly (stays active)
        syncReset = new Button(0, 0, 0, 0, 64, 64, Identifier.fromNamespaceAndPath(Initializer.MOD_ID, "bri"), 2) {
            @Override
            public void onPress() {
                if (screen.owner) {
                    sync.setValue(false);
                    screen.waitForMFInit(() -> screen.sendSync());
                }
            }
        };

        sync.active = screen.owner;

        deleteButton = new Button(0, 0, 0, 0, 64, 64, Identifier.fromNamespaceAndPath(Initializer.MOD_ID, "delete"), 2) {
            @Override
            public void onPress() {
                Initializer.sendPacket(new Delete(screen.getID()));
                onClose();
            }
        };

        deleteButton.active = screen.owner;

        reportButton = new Button(0, 0, 0, 0, 64, 64, Identifier.fromNamespaceAndPath(Initializer.MOD_ID, "report"), 2) {
            @Override
            public void onPress() {
                Initializer.sendPacket(new Report(screen.getID()));
                onClose();
            }
        };

        WidgetSprites textures = new WidgetSprites(Identifier.fromNamespaceAndPath(Initializer.MOD_ID, "widgets/red_button"), Identifier.fromNamespaceAndPath(Initializer.MOD_ID, "widgets/red_button_disabled"), Identifier.fromNamespaceAndPath(Initializer.MOD_ID, "widgets/red_button_highlighted"));

        deleteButton.setTextures(textures);
        reportButton.setTextures(textures);

        if (volume != null) addRenderableWidget(volume);
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

    private void renderTooltipIfHovered(
        GuiGraphics guiGraphics, int mouseX, int mouseY,
        int elementX, int elementY, int elementWidth, int elementHeight,
        List<Component> tooltip
    ) {
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

        if (deleteButton != null) {
            deleteButton.setX(10);
            deleteButton.setY(this.height - vCH - 10);
            deleteButton.setHeight(vCH);
            deleteButton.setWidth(vCH);
        }

        if (reportButton != null) {
            reportButton.setX(this.width - vCH - 10);
            reportButton.setY(this.height - vCH - 10);
            reportButton.setHeight(vCH);
            reportButton.setWidth(vCH);
        }

        // TODO: move this logic to a separate ErrorMenu class
        if (screen != null && screen.errored) {
            if (volume != null) {
                volume.active = false;
            }
            if (renderD != null) {
                renderD.active = false;
            }
            if (quality != null) {
                quality.active = false;
            }
            if (sync != null) {
                sync.active = false;
            }
            if (backButton != null) {
                backButton.active = false;
            }
            if (forwardButton != null) {
                forwardButton.active = false;
            }
            if (pauseButton != null) {
                pauseButton.active = false;
            }
            if (renderDReset != null) {
                renderDReset.active = false;
            }
            if (qualityReset != null) {
                qualityReset.active = false;
            }
            if (syncReset != null) {
                syncReset.active = false;
            }

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

            if (deleteButton != null) deleteButton.render(guiGraphics, mouseX, mouseY, delta);
            if (reportButton != null) reportButton.render(guiGraphics, mouseX, mouseY, delta);

            return;
        }

        // TODO: ??????????????????????
        if (screen != null) {
            if (syncReset != null) {
                syncReset.active = screen.owner && screen.isSync;
            }
            if (renderDReset != null && renderD != null) {
                int currentDistance = (int) (renderD.value * (96 - 24) + 24);
                renderDReset.active = currentDistance != 64;
            }
            if (qualityReset != null) {
                qualityReset.active = !Objects.equals(screen.getQuality(), "720");
            }
        }

        // TODO: this will not work in general
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
        guiGraphics.pose().translate(0, 0); // TODO: see if the 10 offset is necessary, pls let it not be
        renderScreen(guiGraphics, sX, cY, sW, sH);
        guiGraphics.pose().popMatrix();

        cY += sH;
        cY += 5;

        // Settings for volume, backButton, forwardButton, pauseButton
        if (volume != null) {
            volume.setX(this.width / 2 - maxSW / 2);
            volume.setY(cY);
            volume.setHeight(vCH);
            volume.setWidth(Math.min(maxSW / 3, maxSW / 2 - vCH * 9 / 8 - 5));
        }

        if (backButton != null) {
            backButton.setX(this.width / 2 - vCH * 9 / 8);
            backButton.setY(cY);
            backButton.setHeight(vCH);
            backButton.setWidth(vCH);
            backButton.active = !(screen.isSync && !screen.owner);
        }

        if (forwardButton != null) {
            forwardButton.setX(this.width / 2 + vCH / 8);
            forwardButton.setY(cY);
            forwardButton.setHeight(vCH);
            forwardButton.setWidth(vCH);
            forwardButton.active = !(screen.isSync && !screen.owner);
        }

        if (pauseButton != null) {
            pauseButton.setX(this.width / 2 + maxSW / 2 - vCH);
            pauseButton.setY(cY);
            pauseButton.setHeight(vCH);
            pauseButton.setWidth(vCH);
            pauseButton.active = !(screen.isSync && !screen.owner);
        }


        if (sync != null) {
            sync.active = (screen.owner);
        }
        if (deleteButton != null) {
            deleteButton.active = (screen.owner);
        }

        cY += 10 + vCH;

        // Volume, backButton, forwardButton, pauseButton
        if (renderD != null && renderDReset != null) {
            placeButton(vCH, maxSW, cY, renderD, renderDReset);
        }

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
            Component.translatable("dreamdisplays.button.render-distance.tooltip.8", (int) (renderD.value * (96 - 24) + 24)).withStyle(style -> style.withColor(ChatFormatting.GOLD))
        );

        cY += 5 + vCH;

        // quality and qualityReset settings
        if (quality != null && qualityReset != null) {
            placeButton(vCH, maxSW, cY, quality, qualityReset);
        }

        // Setting the quality text and calculating coordinates for tooltip
        Component qualityText = Component.translatable("dreamdisplays.button.quality");
        int qualityTextX = this.width / 2 - maxSW / 2;
        int qualityTextY = cY + vCH / 2 - font.lineHeight / 2;
        guiGraphics.drawString(font, qualityText, qualityTextX, qualityTextY, 0xFFFFFF, true);

        // Tooltip
        List<Component> qualityTooltip = null;
        if (quality != null) {
            qualityTooltip = new ArrayList<>(List.of(
                Component.translatable("dreamdisplays.button.quality.tooltip.1").withStyle(style -> style.withColor(ChatFormatting.WHITE).withBold(true)),
                Component.translatable("dreamdisplays.button.quality.tooltip.2").withStyle(style -> style.withColor(ChatFormatting.GRAY)),
                Component.translatable("dreamdisplays.button.quality.tooltip.3"),
                Component.translatable("dreamdisplays.button.quality.tooltip.4", toQuality((int) (quality.value * screen.getQualityList().size()))).withStyle(style -> style.withColor(ChatFormatting.GOLD))
            ));
        }

        // Add warning if quality is 1080p or higher
        if (Integer.parseInt(screen.getQuality()) >= 1080) {
            qualityTooltip.add(Component.translatable("dreamdisplays.button.quality.tooltip.5").withStyle(style -> style.withColor(ChatFormatting.YELLOW)));
        }

        cY += 15 + vCH;
        if (sync != null && syncReset != null) {
            placeButton(vCH, maxSW, cY, sync, syncReset);
        }

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

        if (deleteButton != null) {
            renderTooltipIfHovered(guiGraphics, mouseX, mouseY, deleteButton.getX(), deleteButton.getY(),
                deleteButton.getWidth(), deleteButton.getHeight(), deleteTooltip);
        }
        if (reportButton != null) {
            renderTooltipIfHovered(guiGraphics, mouseX, mouseY, reportButton.getX(), reportButton.getY(),
                reportButton.getWidth(), reportButton.getHeight(), reportTooltip);
        }

        // Render all child elements (buttons, sliders, etc.)
        for (GuiEventListener child : children()) {
            if (child instanceof Renderable drawable) {
                drawable.render(guiGraphics, mouseX, mouseY, delta);
            }
        }
    }

    // Places button and its reset button at specified coordinates
    private void placeButton(int vCH, int maxSW, int cY, AbstractWidget renderD, Button renderDReset) {
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
    // TODO: it doesn't work
    private void renderScreen(GuiGraphics graphics, int x, int y, int w, int h) {
        if (screen != null && screen.isVideoStarted() && screen.texture != null && screen.renderType != null) {
            Render2D.drawTexturedQuad(graphics.pose(), screen.texture.getTextureView(), x, y, w, h, screen.renderType);
        } else if (screen != null && screen.hasPreviewTexture() && screen.getPreviewTexture() != null && screen.previewRenderType != null) {
            Render2D.drawTexturedQuad(graphics.pose(), screen.getPreviewTexture().getTextureView(), x, y, w, h, screen.previewRenderType);
        } else {
            graphics.fill(x, y, x + w, y + h, 0xFF000000);
        }
    }

    // Converts resolution index to quality string
    private String toQuality(int resolution) {
        List<Integer> list = List.of();
        if (screen != null) {
            list = screen.getQualityList();
        }

        if (list.isEmpty()) return "144";

        int i = Math.max(Math.min(resolution, list.size() - 1), 0);
        return list.get(i).toString();
    }

    // Converts quality string to resolution index
    private int fromQuality(String quality) {
        if (screen == null) return 0;

        List<Integer> list = screen.getQualityList();
        if (list.isEmpty()) return 0;

        int cQ = Integer.parseInt(quality.replace("p", ""));

        int closest = list.getFirst();
        int minDiff = Math.abs(cQ - closest);
        for (int q : list) {
            int diff = Math.abs(q - cQ);
            if (diff < minDiff) {
                minDiff = diff;
                closest = q;
            }
        }

        return list.indexOf(closest);
    }

    // Sets the screen for the display config screen
    private void setScreen(com.dreamdisplays.screen.Screen screen) {
        this.screen = screen;
    }
}

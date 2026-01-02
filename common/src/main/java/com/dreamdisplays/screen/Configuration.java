package com.dreamdisplays.screen;

import com.dreamdisplays.Initializer;
import com.dreamdisplays.net.Packets.Delete;
import com.dreamdisplays.net.Packets.Report;
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
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Configuration of a display screen GUI.
 */
// TODO: rewrite this entire class
@NullMarked
public class Configuration extends Screen {

    @Nullable
    Slider volume = null;

    @Nullable
    Slider renderD = null;

    @Nullable
    Slider quality = null;

    @Nullable
    Slider brightness = null;

    @Nullable
    Toggle sync = null;

    @Nullable
    Button backButton = null;

    @Nullable
    Button forwardButton = null;

    @Nullable
    Button pauseButton = null;

    @Nullable
    Button renderDReset = null;

    @Nullable
    Button qualityReset = null;

    @Nullable
    Button brightnessReset = null;

    @Nullable
    Button volumeReset = null;

    @Nullable
    Button syncReset = null;

    @Nullable
    Button deleteButton = null;

    @Nullable
    Button reportButton = null;

    com.dreamdisplays.screen.@Nullable Screen screen = null;

    protected Configuration() {
        super(Component.translatable("dreamdisplays.ui.title"));
    }

    // Opens the display configuration screen
    public static void open(com.dreamdisplays.screen.Screen screen) {
        Configuration displayConfScreen = new Configuration();
        displayConfScreen.setScreen(screen);
        Minecraft.getInstance().setScreen(displayConfScreen);
    }

    @Override
    protected void init() {
        if (screen != null) volume = new Slider(
                0,
                0,
                0,
                0,
                Component.literal((int) Math.floor(screen.getVolume() * 200) + "%"),
                screen.getVolume()
        ) {
            @Override
            protected void updateMessage() {
                setMessage(
                        Component.literal((int) Math.floor(value * 200) + "%")
                );
            }

            @Override
            protected void applyValue() {
                screen.setVolume((float) value);
            }
        };

        backButton = new Button(
                0,
                0,
                0,
                0,
                64,
                64,
                Identifier.fromNamespaceAndPath(Initializer.MOD_ID, "bbi"),
                2
        ) {
            @Override
            public void onPress() {
                screen.seekBackward();
            }
        };

        forwardButton = new Button(
                0,
                0,
                0,
                0,
                64,
                64,
                Identifier.fromNamespaceAndPath(Initializer.MOD_ID, "bfi"),
                2
        ) {
            @Override
            public void onPress() {
                screen.seekForward();
            }
        };

        pauseButton = new Button(
                0,
                0,
                0,
                0,
                64,
                64,
                Identifier.fromNamespaceAndPath(Initializer.MOD_ID, "bpi"),
                2
        ) {
            @Override
            public void onPress() {
                screen.setPaused(!screen.getPaused());
                setIconTextureId(
                        screen.getPaused()
                                ? Identifier.fromNamespaceAndPath(
                                Initializer.MOD_ID,
                                "bupi"
                        )
                                : Identifier.fromNamespaceAndPath(
                                Initializer.MOD_ID,
                                "bpi"
                        )
                );
            }
        };

        pauseButton.setIconTextureId(
                screen.getPaused()
                        ? Identifier.fromNamespaceAndPath(Initializer.MOD_ID, "bupi")
                        : Identifier.fromNamespaceAndPath(Initializer.MOD_ID, "bpi")
        );

        renderD = new Slider(
                0,
                0,
                0,
                0,
                Component.nullToEmpty(String.valueOf(screen.getRenderDistance())),
                (screen.getRenderDistance() - 24) / (double) (128 - 24)
        ) {
            @Override
            protected void updateMessage() {
                setMessage(
                        Component.nullToEmpty(
                                String.valueOf((int) (value * (128 - 24)) + 24)
                        )
                );
            }

            @Override
            protected void applyValue() {
                int newDistance = (int) (value * (128 - 24) + 24);
                screen.setRenderDistance(newDistance);
                Manager.saveScreenData(screen);
            }
        };

        quality = new Slider(
                0,
                0,
                0,
                0,
                Component.nullToEmpty(screen.getQuality() + "p"),
                ((double) fromQuality(screen.getQuality())) /
                        screen.getQualityList().size()
        ) {
            @Override
            protected void updateMessage() {
                setMessage(
                        Component.nullToEmpty(
                                toQuality(
                                        (int) (value * screen.getQualityList().size())
                                ) +
                                        "p"
                        )
                );
            }

            @Override
            protected void applyValue() {
                screen.setQuality(
                        toQuality((int) (value * screen.getQualityList().size()))
                );
            }
        };

        brightness = new Slider(
                0,
                0,
                0,
                0,
                Component.literal((int) Math.floor(screen.getBrightness() * 100) + "%"),
                screen.getBrightness() / 2.0
        ) {
            @Override
            protected void updateMessage() {
                setMessage(
                        Component.literal((int) Math.floor(value * 200) + "%")
                );
            }

            @Override
            protected void applyValue() {
                screen.setBrightness((float) (value * 2.0));
            }
        };

        renderDReset = new Button(
                0,
                0,
                0,
                0,
                64,
                64,
                Identifier.fromNamespaceAndPath(Initializer.MOD_ID, "bri"),
                2
        ) {
            @Override
            public void onPress() {
                screen.setRenderDistance(Initializer.config.defaultDistance);
                renderD.value =
                        (Initializer.config.defaultDistance - 24) /
                                (double) (128 - 24);
                renderD.setMessage(
                        Component.nullToEmpty(
                                String.valueOf(Initializer.config.defaultDistance)
                        )
                );
                Manager.saveScreenData(screen);
            }
        };

        qualityReset = new Button(
                0,
                0,
                0,
                0,
                64,
                64,
                Identifier.fromNamespaceAndPath(Initializer.MOD_ID, "bri"),
                2
        ) {
            @Override
            public void onPress() {
                int targetIndex = fromQuality("720");
                screen.setQuality(toQuality(targetIndex).replace("p", ""));
                quality.value =
                        (double) targetIndex / screen.getQualityList().size();
                quality.setMessage(
                        Component.nullToEmpty(toQuality(targetIndex) + "p")
                );
            }
        };

        brightnessReset = new Button(
                0,
                0,
                0,
                0,
                64,
                64,
                Identifier.fromNamespaceAndPath(Initializer.MOD_ID, "bri"),
                2
        ) {
            @Override
            public void onPress() {
                screen.setBrightness(1.0f);
                if (brightness != null) {
                    brightness.value = 0.5;
                    brightness.setMessage(Component.literal("100%"));
                }
            }
        };

        volumeReset = new Button(
                0,
                0,
                0,
                0,
                64,
                64,
                Identifier.fromNamespaceAndPath(Initializer.MOD_ID, "bri"),
                2
        ) {
            @Override
            public void onPress() {
                screen.setVolume(0.5f);
                volume.value = 0.5;
                volume.setMessage(Component.literal("100%"));
            }
        };

        sync = new Toggle(
                0,
                0,
                0,
                0,
                Component.translatable(
                        screen.isSync
                                ? "dreamdisplays.button.enabled"
                                : "dreamdisplays.button.disabled"
                ),
                screen.isSync
        ) {
            @Override
            protected void updateMessage() {
                setMessage(
                        Component.translatable(
                                value
                                        ? "dreamdisplays.button.enabled"
                                        : "dreamdisplays.button.disabled"
                        )
                );
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

        syncReset = new Button(
                0,
                0,
                0,
                0,
                64,
                64,
                Identifier.fromNamespaceAndPath(Initializer.MOD_ID, "bri"),
                2
        ) {
            @Override
            public void onPress() {
                if (screen.owner) {
                    sync.setValue(false);
                    screen.waitForMFInit(() -> screen.sendSync());
                }
            }
        };

        sync.active = screen.owner;
        if (brightness != null) {
            brightness.active = !screen.isSync || screen.owner;
        }

        deleteButton = new Button(
                0,
                0,
                0,
                0,
                64,
                64,
                Identifier.fromNamespaceAndPath(Initializer.MOD_ID, "delete"),
                2
        ) {
            @Override
            public void onPress() {
                Settings.removeDisplay(screen.getUUID());
                Manager.unregisterScreen(screen);

                Initializer.sendPacket(new Delete(screen.getUUID()));
                onClose();
            }
        };

        deleteButton.active = screen.owner;

        reportButton = new Button(
                0,
                0,
                0,
                0,
                64,
                64,
                Identifier.fromNamespaceAndPath(Initializer.MOD_ID, "report"),
                2
        ) {
            @Override
            public void onPress() {
                Initializer.sendPacket(new Report(screen.getUUID()));
                onClose();
            }
        };

        if (!Initializer.isReportingEnabled) {
            reportButton = null;
        }

        WidgetSprites sprites = new WidgetSprites(
                Identifier.fromNamespaceAndPath(
                        Initializer.MOD_ID,
                        "widgets/red_button"
                ),
                Identifier.fromNamespaceAndPath(
                        Initializer.MOD_ID,
                        "widgets/red_button_disabled"
                ),
                Identifier.fromNamespaceAndPath(
                        Initializer.MOD_ID,
                        "widgets/red_button_highlighted"
                )
        );

        deleteButton.setSprites(sprites);
        if (reportButton != null) {
            reportButton.setSprites(sprites);
        }

        if (volume != null) addRenderableWidget(volume);
        addRenderableWidget(backButton);
        addRenderableWidget(forwardButton);
        addRenderableWidget(pauseButton);
        addRenderableWidget(renderD);
        addRenderableWidget(quality);
        addRenderableWidget(qualityReset);
        addRenderableWidget(brightness);
        addRenderableWidget(brightnessReset);
        addRenderableWidget(renderDReset);
        addRenderableWidget(volumeReset);
        addRenderableWidget(sync);
        addRenderableWidget(syncReset);
        addRenderableWidget(deleteButton);
        if (reportButton != null) addRenderableWidget(reportButton);
    }

    private void renderTooltipIfHovered(
            GuiGraphics guiGraphics,
            int mouseX,
            int mouseY,
            int elementX,
            int elementY,
            int elementWidth,
            int elementHeight,
            List<Component> tooltip
    ) {
        if (
                mouseX >= elementX &&
                        mouseX <= elementX + elementWidth &&
                        mouseY >= elementY &&
                        mouseY <= elementY + elementHeight
        ) {
            guiGraphics.setComponentTooltipForNextFrame(
                    Minecraft.getInstance().font,
                    tooltip,
                    mouseX,
                    mouseY
            );
        }
    }

    // Renders the display configuration screen
    @Override
    public void render(
            GuiGraphics guiGraphics,
            int mouseX,
            int mouseY,
            float delta
    ) {
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

        if (screen != null && screen.errored) {
            // ugliness, this class really needs to be rewritten
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
            if (volumeReset != null) {
                volumeReset.active = false;
            }
            if (syncReset != null) {
                syncReset.active = false;
            }

            List<Component> errorComponent = List.of(
                    Component.translatable(
                            "dreamdisplays.error.loadingerror.1"
                    ).withStyle(style -> style.withColor(0xff0000)),
                    Component.translatable(
                            "dreamdisplays.error.loadingerror.2"
                    ).withStyle(style -> style.withColor(0xff0000)),
                    Component.translatable(
                            "dreamdisplays.error.loadingerror.3"
                    ).withStyle(style -> style.withColor(0xff0000)),
                    Component.translatable(
                            "dreamdisplays.error.loadingerror.4"
                    ).withStyle(style -> style.withColor(0xff0000)),
                    Component.translatable(
                            "dreamdisplays.error.loadingerror.5"
                    ).withStyle(style -> style.withColor(0xff0000))
            );

            int yP = (int) ((double) this.height / 2 -
                    ((double) (font.lineHeight + 2) * errorComponent.size()) / 2);

            int mW = 0;
            for (Component component : errorComponent) {
                mW = Math.max(font.width(component), mW);
            }

            for (Component component : errorComponent) {
                guiGraphics.drawString(
                        font,
                        component,
                        this.width / 2 - font.width(component) / 2,
                        yP += 2 + font.lineHeight,
                        0xFFFFFFFF,
                        true
                );
            }

            if (deleteButton != null) deleteButton.render(
                    guiGraphics,
                    mouseX,
                    mouseY,
                    delta
            );
            if (reportButton != null) reportButton.render(
                    guiGraphics,
                    mouseX,
                    mouseY,
                    delta
            );

            return;
        }

        if (screen != null) {
            if (syncReset != null) {
                syncReset.active = screen.owner && screen.isSync;
            }
            if (renderDReset != null && renderD != null) {
                int currentDistance = screen.getRenderDistance();
                renderDReset.active =
                        currentDistance != Initializer.config.defaultDistance;
            }
            if (qualityReset != null) {
                qualityReset.active = !Objects.equals(
                        screen.getQuality(),
                        "720"
                );
            }
            if (brightnessReset != null && brightness != null) {
                brightnessReset.active = Math.abs(brightness.value - 0.5) > 0.01; // Allow small floating point tolerance
            }
            if (volumeReset != null && volume != null) {
                volumeReset.active = Math.abs(volume.value - 0.5) > 0.01; // Allow small floating point tolerance
            }
        }

        int headerTextWidth = font.width(headerText);
        int headerTextX = (this.width - headerTextWidth) / 2;
        int headerTextY = 15;
        guiGraphics.drawString(
                font,
                headerText,
                headerTextX,
                headerTextY,
                0xFFFFFFFF,
                true
        );

        int maxSW = this.width / 3;

        // Screen dimensions
        int sW = maxSW;
        int sH = (int) Math.min(
                (int) ((screen.getHeight() / screen.getWidth()) * sW),
                this.height / 3.5
        );
        sW = (int) ((screen.getWidth() / screen.getHeight()) * sH);
        int sX = this.width / 2 - sW / 2;
        int cY = font.lineHeight + 15 * 2;

        guiGraphics.fill(
                this.width / 2 - maxSW / 2,
                cY,
                this.width / 2 + maxSW / 2,
                cY + sH,
                0xff000000
        );
        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate(0, 0); // see if the 10 offset is necessary, pls let it not be
        renderScreen(guiGraphics, sX, cY, sW, sH);
        guiGraphics.pose().popMatrix();

        cY += sH;
        cY += 5;

        // Settings for backButton, forwardButton, pauseButton
        if (backButton != null) {
            backButton.setX(this.width / 2 - maxSW / 2);
            backButton.setY(cY);
            backButton.setHeight(vCH);
            backButton.setWidth(vCH);
            backButton.active = !(screen.isSync && !screen.owner);
        }

        if (forwardButton != null) {
            forwardButton.setX(this.width / 2 - maxSW / 2 + vCH + 5);
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

        // Volume slider and reset button
        if (volume != null && volumeReset != null) {
            placeButton(vCH, maxSW, cY, volume, volumeReset);
        }

        // Volume text
        Component volumeText = Component.translatable(
                "dreamdisplays.button.volume"
        );
        int volumeTextX = this.width / 2 - maxSW / 2;
        int volumeTextY = cY + vCH / 2 - font.lineHeight / 2;
        guiGraphics.drawString(
                font,
                volumeText,
                volumeTextX,
                volumeTextY,
                0xFFFFFFFF,
                true
        );

        // Tooltip for Volume
        List<Component> volumeTooltip = List.of(
                Component.translatable(
                        "dreamdisplays.button.volume.tooltip.1"
                ).withStyle(style ->
                        style.withColor(ChatFormatting.WHITE).withBold(true)
                ),
                Component.translatable(
                        "dreamdisplays.button.volume.tooltip.2"
                ).withStyle(style -> style.withColor(ChatFormatting.GRAY)),
                Component.translatable(
                        "dreamdisplays.button.volume.tooltip.3"
                ).withStyle(style -> style.withColor(ChatFormatting.GRAY)),
                Component.translatable(
                        "dreamdisplays.button.volume.tooltip.4",
                        (int) (volume.value * 200)
                ).withStyle(style -> style.withColor(ChatFormatting.GOLD))
        );

        cY += 5 + vCH;

        // Render Distance slider and reset button
        if (renderD != null && renderDReset != null) {
            placeButton(vCH, maxSW, cY, renderD, renderDReset);
        }

        // Tooltip for Render Distance
        Component renderDComponent = Component.translatable(
                "dreamdisplays.button.render-distance"
        );
        int renderDTextX = this.width / 2 - maxSW / 2;
        int renderDTextY = cY + vCH / 2 - font.lineHeight / 2;
        guiGraphics.drawString(
                font,
                renderDComponent,
                renderDTextX,
                renderDTextY,
                0xFFFFFFFF,
                true
        );

        // Tooltip
        List<Component> renderDTooltip = List.of(
                Component.translatable(
                        "dreamdisplays.button.render-distance.tooltip.1"
                ).withStyle(style ->
                        style.withColor(ChatFormatting.WHITE).withBold(true)
                ),
                Component.translatable(
                        "dreamdisplays.button.render-distance.tooltip.2"
                ).withStyle(style -> style.withColor(ChatFormatting.GRAY)),
                Component.translatable(
                        "dreamdisplays.button.render-distance.tooltip.3"
                ).withStyle(style -> style.withColor(ChatFormatting.GRAY)),
                Component.translatable(
                        "dreamdisplays.button.render-distance.tooltip.4"
                ),
                Component.translatable(
                        "dreamdisplays.button.render-distance.tooltip.5"
                ).withStyle(style -> style.withColor(ChatFormatting.DARK_GRAY)),
                Component.translatable(
                        "dreamdisplays.button.render-distance.tooltip.6"
                ).withStyle(style -> style.withColor(ChatFormatting.DARK_GRAY)),
                Component.translatable(
                        "dreamdisplays.button.render-distance.tooltip.7"
                ),
                Component.translatable(
                        "dreamdisplays.button.render-distance.tooltip.8",
                        (int) (renderD.value * (128 - 24) + 24)
                ).withStyle(style -> style.withColor(ChatFormatting.GOLD))
        );

        cY += 5 + vCH;

        // quality and qualityReset settings
        if (quality != null && qualityReset != null) {
            placeButton(vCH, maxSW, cY, quality, qualityReset);
        }

        // Setting the quality text and calculating coordinates for tooltip
        Component qualityComponent = Component.translatable(
                "dreamdisplays.button.quality"
        );
        int qualityTextX = this.width / 2 - maxSW / 2;
        int qualityTextY = cY + vCH / 2 - font.lineHeight / 2;
        guiGraphics.drawString(
                font,
                qualityComponent,
                qualityTextX,
                qualityTextY,
                0xFFFFFFFF,
                true
        );

        // Tooltip
        List<Component> qualityTooltip = null;
        if (quality != null) {
            qualityTooltip = new ArrayList<>(
                    List.of(
                            Component.translatable(
                                    "dreamdisplays.button.quality.tooltip.1"
                            ).withStyle(style ->
                                    style.withColor(ChatFormatting.WHITE).withBold(true)
                            ),
                            Component.translatable(
                                    "dreamdisplays.button.quality.tooltip.2"
                            ).withStyle(style -> style.withColor(ChatFormatting.GRAY)),
                            Component.translatable(
                                    "dreamdisplays.button.quality.tooltip.3"
                            ),
                            Component.translatable(
                                    "dreamdisplays.button.quality.tooltip.4",
                                    toQuality(
                                            (int) (quality.value *
                                                    screen.getQualityList().size())
                                    )
                            ).withStyle(style -> style.withColor(ChatFormatting.GOLD))
                    )
            );
        }

        // Add warning if quality is 1080p or higher
        if (Integer.parseInt(screen.getQuality()) >= 1080) {
            qualityTooltip.add(
                    Component.translatable(
                            "dreamdisplays.button.quality.tooltip.5"
                    ).withStyle(style -> style.withColor(ChatFormatting.YELLOW))
            );
        }

        cY += 5 + vCH;

        // brightness and brightnessReset settings
        if (brightness != null && brightnessReset != null) {
            placeButton(vCH, maxSW, cY, brightness, brightnessReset);
        }

        // Setting the brightness text and calculating coordinates for tooltip
        Component brightnessComponent = Component.translatable(
                "dreamdisplays.button.brightness"
        );
        int brightnessTextX = this.width / 2 - maxSW / 2;
        int brightnessTextY = cY + vCH / 2 - font.lineHeight / 2;
        guiGraphics.drawString(
                font,
                brightnessComponent,
                brightnessTextX,
                brightnessTextY,
                0xFFFFFFFF,
                true
        );

        List<Component> brightnessTooltip = List.of(
                Component.translatable(
                        "dreamdisplays.button.brightness.tooltip.1"
                ).withStyle(style ->
                        style.withColor(ChatFormatting.WHITE).withBold(true)
                ),
                Component.translatable(
                        "dreamdisplays.button.brightness.tooltip.2"
                ).withStyle(style -> style.withColor(ChatFormatting.GRAY)),
                Component.translatable(
                        "dreamdisplays.button.brightness.tooltip.3",
                        brightness != null ? (int) Math.floor(brightness.value * 200) : 100
                ).withStyle(style -> style.withColor(ChatFormatting.GOLD))
        );

        cY += 15 + vCH;
        if (sync != null && syncReset != null) {
            placeButton(vCH, maxSW, cY, sync, syncReset);
        }

        // Setting the sync text and calculating coordinates for the tooltip
        Component syncComponent = Component.translatable(
                "dreamdisplays.button.synchronization"
        );
        int syncTextX = this.width / 2 - maxSW / 2;
        int syncTextY = cY + vCH / 2 - font.lineHeight / 2;
        guiGraphics.drawString(
                font,
                syncComponent,
                syncTextX,
                syncTextY,
                0xFFFFFFFF,
                true
        );

        List<Component> syncTooltip = List.of(
                Component.translatable(
                        "dreamdisplays.button.synchronization.tooltip.1"
                ).withStyle(style ->
                        style.withColor(ChatFormatting.WHITE).withBold(true)
                ),
                Component.translatable(
                        "dreamdisplays.button.synchronization.tooltip.2"
                ).withStyle(style -> style.withColor(ChatFormatting.GRAY)),
                Component.translatable(
                        "dreamdisplays.button.synchronization.tooltip.3"
                ).withStyle(style -> style.withColor(ChatFormatting.GRAY)),
                Component.translatable(
                        "dreamdisplays.button.synchronization.tooltip.4"
                ),
                Component.translatable(
                        "dreamdisplays.button.synchronization.tooltip.5",
                        sync.value
                                ? Component.translatable("dreamdisplays.button.enabled")
                                : Component.translatable("dreamdisplays.button.disabled")
                ).withStyle(style -> style.withColor(ChatFormatting.GOLD))
        );

        renderTooltipIfHovered(
                guiGraphics,
                mouseX,
                mouseY,
                volumeTextX,
                volumeTextY,
                font.width(volumeText),
                font.lineHeight,
                volumeTooltip
        );
        renderTooltipIfHovered(
                guiGraphics,
                mouseX,
                mouseY,
                renderDTextX,
                renderDTextY,
                font.width(renderDComponent),
                font.lineHeight,
                renderDTooltip
        );
        renderTooltipIfHovered(
                guiGraphics,
                mouseX,
                mouseY,
                qualityTextX,
                qualityTextY,
                font.width(qualityComponent),
                font.lineHeight,
                qualityTooltip
        );
        renderTooltipIfHovered(
                guiGraphics,
                mouseX,
                mouseY,
                brightnessTextX,
                brightnessTextY,
                font.width(brightnessComponent),
                font.lineHeight,
                brightnessTooltip
        );
        renderTooltipIfHovered(
                guiGraphics,
                mouseX,
                mouseY,
                syncTextX,
                syncTextY,
                font.width(syncComponent),
                font.lineHeight,
                syncTooltip
        );

        // Tooltips for delete and report buttons
        List<Component> deleteTooltip = List.of(
                Component.translatable(
                        "dreamdisplays.button.delete.tooltip.1"
                ).withStyle(style ->
                        style.withColor(ChatFormatting.WHITE).withBold(true)
                ),
                Component.translatable(
                        "dreamdisplays.button.delete.tooltip.2"
                ).withStyle(style -> style.withColor(ChatFormatting.GRAY))
        );

        List<Component> reportTooltip = List.of(
                Component.translatable(
                        "dreamdisplays.button.report.tooltip.1"
                ).withStyle(style ->
                        style.withColor(ChatFormatting.WHITE).withBold(true)
                ),
                Component.translatable(
                        "dreamdisplays.button.report.tooltip.2"
                ).withStyle(style -> style.withColor(ChatFormatting.GRAY))
        );

        if (deleteButton != null) {
            renderTooltipIfHovered(
                    guiGraphics,
                    mouseX,
                    mouseY,
                    deleteButton.getX(),
                    deleteButton.getY(),
                    deleteButton.getWidth(),
                    deleteButton.getHeight(),
                    deleteTooltip
            );
        }
        if (reportButton != null) {
            renderTooltipIfHovered(
                    guiGraphics,
                    mouseX,
                    mouseY,
                    reportButton.getX(),
                    reportButton.getY(),
                    reportButton.getWidth(),
                    reportButton.getHeight(),
                    reportTooltip
            );
        }

        // Render all child elements (buttons, sliders, etc.)
        for (GuiEventListener child : children()) {
            if (child instanceof Renderable drawable) {
                drawable.render(guiGraphics, mouseX, mouseY, delta);
            }
        }
    }

    // Places button and its reset button at specified coordinates
    private void placeButton(
            int vCH,
            int maxSW,
            int cY,
            AbstractWidget renderD,
            Button renderDReset
    ) {
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
    private void renderScreen(
            GuiGraphics guiGraphics,
            int x,
            int y,
            int w,
            int h
    ) {
        if (
                screen != null &&
                        screen.isVideoStarted() &&
                        screen.texture != null &&
                        screen.textureId != null
        ) {
            screen.fitTexture();
            guiGraphics.blit(
                    RenderPipelines.GUI_TEXTURED,
                    screen.textureId,
                    x,
                    y,
                    0f,
                    0f,
                    w,
                    h,
                    screen.textureWidth,
                    screen.textureHeight,
                    screen.textureWidth,
                    screen.textureHeight
            );
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

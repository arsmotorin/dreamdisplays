package com.dreamdisplays.screen;

import com.dreamdisplays.Initializer;
import com.dreamdisplays.net.Packets.Delete;
import com.dreamdisplays.net.Packets.Report;
import com.dreamdisplays.screen.widgets.Button;
import com.dreamdisplays.screen.widgets.ProgressSlider;
import com.dreamdisplays.screen.widgets.Slider;
import com.dreamdisplays.screen.widgets.SuggestionsPanel;
import com.dreamdisplays.screen.widgets.Toggle;
import com.dreamdisplays.ytdlp.Thumbnails;
import com.dreamdisplays.ytdlp.YtDlp;
import com.dreamdisplays.ytdlp.YtVideoInfo;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Configuration of a display screen GUI.
 */
@NullMarked
public class Configuration extends Screen {

    @Nullable Slider volume = null;
    @Nullable Slider renderD = null;
    @Nullable Slider quality = null;
    @Nullable Slider brightness = null;
    @Nullable Toggle sync = null;
    @Nullable Button backButton = null;
    @Nullable Button forwardButton = null;
    @Nullable Button pauseButton = null;
    @Nullable Button renderDReset = null;
    @Nullable Button qualityReset = null;
    @Nullable Button brightnessReset = null;
    @Nullable Button volumeReset = null;
    @Nullable Button syncReset = null;
    @Nullable Button deleteButton = null;
    @Nullable Button reportButton = null;
    @Nullable ProgressSlider progress = null;

    @Nullable SuggestionsPanel suggestions = null;
    @Nullable String lastSuggestedVideoId = null;

    private static final int PADDING = 10;
    private static final int PANEL_GAP = 8;
    private static final int PANEL_PADDING_X = 10;
    private static final int PANEL_PADDING_Y = 10;
    private static final int ROW_GAP = 4;
    private static final int CTRL_BTN = 22;
    private static final int ROW_H = CTRL_BTN;
    private static final int RESET_W = CTRL_BTN;
    private static final int CONTROL_W = 130;
    private static final int PANEL_BG = 0x90101010;
    private static final int PANEL_BORDER = 0xFF606060;
    private static final int ROW_BG = 0x40000000;

    private @Nullable HoverArea volumeHover, renderDHover, qualityHover, brightnessHover, syncHover;

    com.dreamdisplays.screen.@Nullable Screen screen = null;

    protected Configuration() {
        super(Component.translatable("dreamdisplays.ui.title"));
    }

    public static void open(com.dreamdisplays.screen.Screen screen) {
        Configuration s = new Configuration();
        s.setScreen(screen);
        Minecraft.getInstance().setScreen(s);
    }

    private void setScreen(com.dreamdisplays.screen.Screen s) {
        this.screen = s;
    }

    @Override
    protected void init() {
        if (screen == null) return;

        volume = new Slider(0, 0, 0, 0,
                Component.literal((int) Math.floor(screen.getVolume() * 200) + "%"),
                screen.getVolume()) {
            @Override protected void updateMessage() {
                setMessage(Component.literal((int) Math.floor(value * 200) + "%"));
            }
            @Override protected void applyValue() { screen.setVolume((float) value); }
        };

        backButton = iconButton("bbi", () -> screen.seekBackward());
        forwardButton = iconButton("bfi", () -> screen.seekForward());
        pauseButton = new Button(0, 0, 0, 0, 64, 64,
                Identifier.fromNamespaceAndPath(Initializer.MOD_ID, "bpi"), 2) {
            @Override public void onPress() {
                screen.setPaused(!screen.getPaused());
                setIconTextureId(Identifier.fromNamespaceAndPath(Initializer.MOD_ID,
                        screen.getPaused() ? "bupi" : "bpi"));
            }
        };
        pauseButton.setIconTextureId(Identifier.fromNamespaceAndPath(Initializer.MOD_ID,
                screen.getPaused() ? "bupi" : "bpi"));

        progress = new ProgressSlider(0, 0, 100, CTRL_BTN,
                () -> screen != null ? screen.getCurrentTimeNanos() : 0,
                () -> screen != null ? screen.getMediaPlayerDurationNanos() : 0,
                nanos -> {
                    if (screen != null && screen.canSeek() && !screen.isLive()) {
                        screen.seekToMillis(nanos / 1_000_000L);
                    }
                });

        renderD = new Slider(0, 0, 0, 0,
                Component.literal(screen.getRenderDistance() + " blocks"),
                (screen.getRenderDistance() - 24) / (double) (128 - 24)) {
            @Override protected void updateMessage() {
                setMessage(Component.literal(((int) (value * (128 - 24)) + 24) + " blocks"));
            }
            @Override protected void applyValue() {
                screen.setRenderDistance((int) (value * (128 - 24) + 24));
                Manager.saveScreenData(screen);
            }
        };

        quality = new Slider(0, 0, 0, 0,
                Component.literal(screen.getQuality() + "p"),
                qualityFraction(screen.getQuality())) {
            @Override protected void updateMessage() {
                setMessage(Component.literal(qualityFromFraction(value) + "p"));
            }
            @Override protected void applyValue() { screen.setQuality(qualityFromFraction(value)); }
        };

        brightness = new Slider(0, 0, 0, 0,
                Component.literal((int) Math.floor(screen.getBrightness() * 100) + "%"),
                screen.getBrightness() / 2.0) {
            @Override protected void updateMessage() {
                setMessage(Component.literal((int) Math.floor(value * 200) + "%"));
            }
            @Override protected void applyValue() { screen.setBrightness((float) (value * 2.0)); }
        };

        renderDReset = resetButton(() -> {
            screen.setRenderDistance(Initializer.config.defaultDistance);
            renderD.value = (Initializer.config.defaultDistance - 24) / (double) (128 - 24);
            renderD.setMessage(Component.literal(Initializer.config.defaultDistance + " blocks"));
            Manager.saveScreenData(screen);
        });
        qualityReset = resetButton(() -> {
            screen.setQuality("720");
            quality.value = qualityFraction("720");
            quality.setMessage(Component.literal("720p"));
        });
        brightnessReset = resetButton(() -> {
            screen.setBrightness(1.0f);
            if (brightness != null) {
                brightness.value = 0.5;
                brightness.setMessage(Component.literal("100%"));
            }
        });
        volumeReset = resetButton(() -> {
            screen.setVolume(0.5f);
            volume.value = 0.5;
            volume.setMessage(Component.literal("100%"));
        });

        sync = new Toggle(0, 0, 0, 0,
                Component.translatable(screen.isSync ? "dreamdisplays.button.enabled"
                        : "dreamdisplays.button.disabled"),
                screen.isSync) {
            @Override protected void updateMessage() {
                setMessage(Component.translatable(value ? "dreamdisplays.button.enabled"
                        : "dreamdisplays.button.disabled"));
            }
            @Override public void applyValue() {
                if (screen.owner && syncReset != null) {
                    screen.isSync = value;
                    syncReset.active = !value;
                    screen.waitForMFInit(() -> screen.sendSync());
                }
            }
        };
        syncReset = resetButton(() -> {
            if (screen.owner && sync != null) {
                sync.setValue(false);
                screen.waitForMFInit(() -> screen.sendSync());
            }
        });
        sync.active = screen.owner;
        if (brightness != null) brightness.active = !screen.isSync || screen.owner;

        WidgetSprites red = new WidgetSprites(
                Identifier.fromNamespaceAndPath(Initializer.MOD_ID, "widgets/red_button"),
                Identifier.fromNamespaceAndPath(Initializer.MOD_ID, "widgets/red_button_disabled"),
                Identifier.fromNamespaceAndPath(Initializer.MOD_ID, "widgets/red_button_highlighted"));

        deleteButton = new Button(0, 0, 0, 0, 64, 64,
                Identifier.fromNamespaceAndPath(Initializer.MOD_ID, "delete"), 2) {
            @Override public void onPress() {
                Settings.removeDisplay(screen.getUUID());
                Manager.unregisterScreen(screen);
                Initializer.sendPacket(new Delete(screen.getUUID()));
                onClose();
            }
        };
        deleteButton.setSprites(red);
        deleteButton.active = screen.owner;

        if (Initializer.isReportingEnabled) {
            reportButton = new Button(0, 0, 0, 0, 64, 64,
                    Identifier.fromNamespaceAndPath(Initializer.MOD_ID, "report"), 2) {
                @Override public void onPress() {
                    Initializer.sendPacket(new Report(screen.getUUID()));
                    onClose();
                }
            };
            reportButton.setSprites(red);
        } else {
            reportButton = null;
        }

        addRenderableWidget(volume);
        addRenderableWidget(backButton);
        addRenderableWidget(forwardButton);
        addRenderableWidget(progress);
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

        suggestions = new SuggestionsPanel(0, 0, 100, 100, this::onPickSuggested);
        addRenderableWidget(suggestions);
    }

    private Button iconButton(String icon, Runnable action) {
        return new Button(0, 0, 0, 0, 64, 64,
                Identifier.fromNamespaceAndPath(Initializer.MOD_ID, icon), 2) {
            @Override public void onPress() { action.run(); }
        };
    }

    private Button resetButton(Runnable action) {
        return new Button(0, 0, 0, 0, 64, 64,
                Identifier.fromNamespaceAndPath(Initializer.MOD_ID, "bri"), 2) {
            @Override public void onPress() { action.run(); }
        };
    }

    private void onPickSuggested(YtVideoInfo info) {
        if (screen == null) return;
        screen.playSuggestedVideo(info.getWatchUrl(),
                screen.getLang() == null ? "" : screen.getLang());
        // Cache the title we already have so the preview overlay shows it instantly
        com.dreamdisplays.ytdlp.VideoTitleCache.put(info.getId(), info.getTitle());
        lastSuggestedVideoId = info.getId();
        if (suggestions != null) suggestions.setRelatedTo(info.getId());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        renderTransparentBackground(g);

        if (screen == null) {
            super.render(g, mouseX, mouseY, delta);
            return;
        }

        int titleY = 6;
        renderModLabel(g, PADDING, titleY);

        boolean videoReady = screen.isVideoStarted() && !screen.errored;
        if (syncReset != null) syncReset.active = videoReady && screen.owner && screen.isSync;
        if (renderDReset != null) renderDReset.active = videoReady && screen.getRenderDistance() != Initializer.config.defaultDistance;
        if (qualityReset != null) qualityReset.active = videoReady && !Objects.equals(screen.getQuality(), "720");
        if (brightnessReset != null && brightness != null) brightnessReset.active = videoReady && Math.abs(brightness.value - 0.5) > 0.01;
        if (volumeReset != null && volume != null) volumeReset.active = videoReady && Math.abs(volume.value - 0.5) > 0.01;

        boolean enabled = videoReady;
        if (volume != null) volume.active = enabled;
        if (renderD != null) renderD.active = enabled;
        if (quality != null) quality.active = enabled;
        if (brightness != null) brightness.active = enabled && (!screen.isSync || screen.owner);
        if (sync != null) sync.active = enabled && screen.owner;
        if (deleteButton != null) deleteButton.active = screen.owner; // delete always available
        if (progress != null) progress.active = enabled && screen.canSeek() && !screen.isLive();

        if (screen.errored) {
            renderErroredOverlay(g, mouseX, mouseY, delta);
            return;
        }

        int contentTop = titleY + font.lineHeight + 8;
        int contentBottom = this.height - PADDING;
        int totalW = this.width - PADDING * 2;
        int totalH = contentBottom - contentTop;
        boolean compact = totalW < 600;

        int topRowH = Math.max(220, (totalH * 6) / 10);
        int suggestionsH = totalH - topRowH - PANEL_GAP;
        if (suggestionsH < 160) {
            suggestionsH = 160;
            topRowH = totalH - suggestionsH - PANEL_GAP;
        }

        int leftX = PADDING;
        int previewW;
        int settingsW;
        int settingsX;
        int previewH;
        int settingsH;
        int settingsY;

        if (compact) {
            previewW = totalW;
            previewH = Math.min(220, topRowH * 3 / 5);
            settingsW = totalW;
            settingsH = topRowH - previewH - PANEL_GAP;
            settingsX = leftX;
            settingsY = contentTop + previewH + PANEL_GAP;
        } else {
            previewW = (totalW * 6) / 10 - PANEL_GAP / 2;
            settingsW = totalW - previewW - PANEL_GAP;
            settingsX = leftX + previewW + PANEL_GAP;
            previewH = topRowH;
            settingsH = topRowH;
            settingsY = contentTop;
        }

        int previewX = leftX;
        int previewY = contentTop;

        int suggestionsX = leftX;
        int suggestionsY = contentTop + topRowH + PANEL_GAP;
        int suggestionsW = totalW;

        drawPanel(g, previewX, previewY, previewW, previewH, "Preview"); // TODO: translation
        drawPanel(g, settingsX, settingsY, settingsW, settingsH, "Display settings"); // TODO: translation

        renderPreviewSection(g, previewX, previewY, previewW, previewH);
        renderSettingsSection(g, settingsX, settingsY, settingsW, settingsH);

        if (suggestions != null) {
            suggestions.visible = true;
            suggestions.setX(suggestionsX);
            suggestions.setY(suggestionsY);
            suggestions.setWidth(suggestionsW);
            suggestions.setHeight(suggestionsH);

            String currentId = YtDlp.extractVideoId(screen.getVideoUrl());
            if (currentId != null && !Objects.equals(currentId, lastSuggestedVideoId)) {
                lastSuggestedVideoId = currentId;
                suggestions.setRelatedTo(currentId);
            }
        }

        layoutOwnerActions(settingsX, settingsY, settingsW, settingsH);

        super.render(g, mouseX, mouseY, delta);

        renderTooltips(g, mouseX, mouseY);
    }

    private void renderErroredOverlay(GuiGraphics g, int mouseX, int mouseY, float delta) {
        if (suggestions != null) suggestions.visible = false;
        if (volume != null) volume.active = false;
        if (renderD != null) renderD.active = false;
        if (quality != null) quality.active = false;
        if (brightness != null) brightness.active = false;
        if (sync != null) sync.active = false;
        if (backButton != null) backButton.active = false;
        if (forwardButton != null) forwardButton.active = false;
        if (pauseButton != null) pauseButton.active = false;
        if (renderDReset != null) renderDReset.active = false;
        if (qualityReset != null) qualityReset.active = false;
        if (brightnessReset != null) brightnessReset.active = false;
        if (volumeReset != null) volumeReset.active = false;
        if (syncReset != null) syncReset.active = false;
        if (progress != null) progress.active = false;

        int panelW = Math.min(420, this.width - 40);
        int panelX = this.width / 2 - panelW / 2;
        int panelY = this.height / 2 - 70;
        drawPanel(g, panelX, panelY, panelW, 130, "Display error"); // TODO: translation
        List<Component> lines = List.of(
                Component.translatable("dreamdisplays.error.loadingerror.1").withStyle(s -> s.withColor(ChatFormatting.RED)),
                Component.translatable("dreamdisplays.error.loadingerror.2").withStyle(s -> s.withColor(ChatFormatting.RED)),
                Component.translatable("dreamdisplays.error.loadingerror.4").withStyle(s -> s.withColor(ChatFormatting.GRAY)),
                Component.translatable("dreamdisplays.error.loadingerror.5").withStyle(s -> s.withColor(ChatFormatting.GRAY))
        );
        int y = panelY + headerHeight() + 8;
        for (Component line : lines) {
            g.drawString(font, line, this.width / 2 - font.width(line) / 2, y, 0xFFFFFFFF, false);
            y += font.lineHeight + 4;
        }
        if (deleteButton != null) {
            deleteButton.setX(panelX + panelW / 2 - 22);
            deleteButton.setY(panelY + 130 - 24);
            deleteButton.setWidth(20);
            deleteButton.setHeight(20);
        }
        if (reportButton != null) {
            reportButton.setX(panelX + panelW / 2 + 2);
            reportButton.setY(panelY + 130 - 24);
            reportButton.setWidth(20);
            reportButton.setHeight(20);
        }
        super.render(g, mouseX, mouseY, delta);
    }

    private void renderPreviewSection(GuiGraphics g, int px, int py, int pw, int ph) {
        com.dreamdisplays.screen.Screen scr = Objects.requireNonNull(screen);
        int innerX = px + PANEL_PADDING_X;
        int innerY = py + headerHeight();
        int innerW = pw - PANEL_PADDING_X * 2;

        int controlsRowY = py + ph - PANEL_PADDING_Y - CTRL_BTN;
        int controlsLeft = innerX;
        int controlsRight = innerX + innerW;

        int previewMaxH = controlsRowY - innerY - 6;
        int frameW = innerW;
        int frameH = previewMaxH;
        g.fill(innerX, innerY, innerX + frameW, innerY + frameH, 0xFF000000);

        float ratio = scr.getWidth() / Math.max(1f, scr.getHeight());
        int videoW;
        int videoH;
        if (frameW / (float) frameH > ratio) {
            videoH = frameH;
            videoW = (int) (videoH * ratio);
        } else {
            videoW = frameW;
            videoH = (int) (videoW / ratio);
        }
        int videoX = innerX + (frameW - videoW) / 2;
        int videoY = innerY + (frameH - videoH) / 2;

        if (scr.isVideoStarted() && scr.texture != null && scr.textureId != null) {
            scr.fitTexture();
            g.blit(RenderPipelines.GUI_TEXTURED, scr.textureId,
                    videoX, videoY, 0F, 0F, videoW, videoH,
                    scr.textureWidth, scr.textureHeight,
                    scr.textureWidth, scr.textureHeight);
        } else {
            Identifier thumb = currentThumbnail();
            if (thumb != null) {
                g.blit(RenderPipelines.GUI_TEXTURED, thumb,
                        videoX, videoY, 0F, 0F, videoW, videoH, 320, 180);
                g.fill(videoX, videoY, videoX + videoW, videoY + videoH, 0x80000000);
            }
            String waiting = Component.translatable("dreamdisplays.ui.waiting").getString();
            g.drawString(font, waiting,
                    innerX + frameW / 2 - font.width(waiting) / 2,
                    innerY + frameH / 2 - font.lineHeight / 2,
                    0xFFCCCCCC, true);
        }

        renderTitleOverlay(g, scr, innerX, innerY + frameH, frameW);

        boolean canSeek = !(scr.isSync && !scr.owner) && scr.canSeek();
        if (backButton != null) {
            backButton.setX(controlsLeft);
            backButton.setY(controlsRowY);
            backButton.setWidth(CTRL_BTN);
            backButton.setHeight(CTRL_BTN);
            backButton.active = canSeek;
        }
        if (forwardButton != null) {
            forwardButton.setX(controlsLeft + CTRL_BTN + 4);
            forwardButton.setY(controlsRowY);
            forwardButton.setWidth(CTRL_BTN);
            forwardButton.setHeight(CTRL_BTN);
            forwardButton.active = canSeek;
        }
        if (pauseButton != null) {
            pauseButton.setX(controlsRight - CTRL_BTN);
            pauseButton.setY(controlsRowY);
            pauseButton.setWidth(CTRL_BTN);
            pauseButton.setHeight(CTRL_BTN);
            // Always enabled (modulo sync) — the pause button is the user's only way
            // to kick off playback when the saved state is "paused", so blocking it
            // on isVideoStarted leaves them stuck on "Waiting for video".
            pauseButton.active = !(scr.isSync && !scr.owner);
        }
        if (progress != null) {
            int progX = controlsLeft + CTRL_BTN * 2 + 8;
            int progRight = controlsRight - CTRL_BTN - 4;
            int progW = Math.max(40, progRight - progX);
            progress.setX(progX);
            progress.setY(controlsRowY);
            progress.setWidth(progW);
            progress.setHeight(CTRL_BTN);
        }
    }

    private void renderTitleOverlay(GuiGraphics g, com.dreamdisplays.screen.Screen scr,
                                    int x, int y, int w) {
        String videoId = YtDlp.extractVideoId(scr.getVideoUrl());
        com.dreamdisplays.ytdlp.YtVideoInfo meta = videoId != null
                ? com.dreamdisplays.ytdlp.VideoMetadataCache.get(videoId) : null;
        if (videoId != null && meta == null) {
            com.dreamdisplays.ytdlp.VideoMetadataCache.requestAsync(videoId);
        }

        String title = meta != null ? meta.getTitle() : null;
        if (title == null || title.isEmpty()) title = scr.getVideoUrl();
        if (title == null) title = "—";

        String channel = meta != null ? meta.getUploader() : null;
        String views = meta != null ? meta.formatViews() : "";
        String likes = meta != null ? meta.formatLikes() : "";
        String published = meta != null ? meta.getPublishedText() : null;
        boolean isNew = meta != null && meta.isRecent(7);

        int padX = 4;
        int padY = 3;
        int textW = w - padX * 2;
        String shown = trimToWidth(title, textW);

        int boxH = font.lineHeight * 2 + padY * 3;
        int boxY = y - boxH;
        g.fill(x, boxY, x + w, y, 0xC0000000);

        int titleX = x + padX;
        int titleY = boxY + padY;
        if (isNew) {
            String tag = "New"; // TODO: translation
            int tw = font.width(tag) + 6;
            int th = font.lineHeight;
            g.fill(titleX, titleY - 1, titleX + tw, titleY + th, 0xFFE53935);
            g.drawString(font, tag, titleX + 3, titleY, 0xFFFFFFFF, false);
            titleX += tw + 4;
            shown = trimToWidth(title, textW - tw - 4);
        }
        g.drawString(font, shown, titleX, titleY, 0xFFFFFFFF, false);

        StringBuilder meta2 = new StringBuilder();
        if (channel != null && !channel.isEmpty()) meta2.append(channel);
        if (!views.isEmpty()) {
            if (meta2.length() > 0) meta2.append(" • ");
            meta2.append(views);
        }
        if (!likes.isEmpty()) {
            if (meta2.length() > 0) meta2.append(" • ");
            meta2.append(likes).append(" likes"); // TODO: translation
        }
        if (published != null && !published.isEmpty()) {
            if (meta2.length() > 0) meta2.append(" • ");
            meta2.append(published);
        }
        String metaShown = trimToWidth(meta2.toString(), textW);
        g.drawString(font, metaShown, x + padX,
                boxY + padY + font.lineHeight + padY, 0xFFAAAAAA, false);
    }

    private String trimToWidth(String s, int maxW) {
        if (font.width(s) <= maxW) return s;
        String dots = "...";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (font.width(sb.toString() + s.charAt(i) + dots) > maxW) break;
            sb.append(s.charAt(i));
        }
        return sb + dots;
    }

    private @Nullable Identifier currentThumbnail() {
        if (screen == null) return null;
        String url = screen.getVideoUrl();
        if (url == null) return null;
        String id = YtDlp.extractVideoId(url);
        if (id == null) return null;
        Identifier ready = Thumbnails.get(id);
        if (ready != null) return ready;
        Thumbnails.request(id, "https://i.ytimg.com/vi/" + id + "/mqdefault.jpg");
        return null;
    }

    private void renderSettingsSection(GuiGraphics g, int px, int py, int pw, int ph) {
        int innerX = px + PANEL_PADDING_X;
        int innerY = py + headerHeight();
        int innerW = pw - PANEL_PADDING_X * 2;

        int rowY = innerY;
        int volumeRowY = rowY;
        rowY = renderRow(g, innerX, rowY, innerW, "dreamdisplays.button.volume", volume, volumeReset);
        int renderDRowY = rowY;
        rowY = renderRow(g, innerX, rowY, innerW, "dreamdisplays.button.render-distance", renderD, renderDReset);
        int qualityRowY = rowY;
        rowY = renderRow(g, innerX, rowY, innerW, "dreamdisplays.button.quality", quality, qualityReset);
        int brightnessRowY = rowY;
        rowY = renderRow(g, innerX, rowY, innerW, "dreamdisplays.button.brightness", brightness, brightnessReset);
        rowY += 6;
        int syncRowY = rowY;
        renderRow(g, innerX, rowY, innerW, "dreamdisplays.button.synchronization", sync, syncReset);

        volumeHover = labelHover(innerX + 6, volumeRowY, "dreamdisplays.button.volume");
        renderDHover = labelHover(innerX + 6, renderDRowY, "dreamdisplays.button.render-distance");
        qualityHover = labelHover(innerX + 6, qualityRowY, "dreamdisplays.button.quality");
        brightnessHover = labelHover(innerX + 6, brightnessRowY, "dreamdisplays.button.brightness");
        syncHover = labelHover(innerX + 6, syncRowY, "dreamdisplays.button.synchronization");
    }

    private HoverArea labelHover(int x, int rowY, String key) {
        int w = font.width(Component.translatable(key));
        int textY = rowY + ROW_H / 2 - font.lineHeight / 2;
        return new HoverArea(x, textY, w, font.lineHeight);
    }

    private int renderRow(GuiGraphics g, int x, int y, int w, String key,
                          @Nullable AbstractWidget control, @Nullable Button reset) {
        g.fill(x, y, x + w, y + ROW_H, ROW_BG);
        Component label = Component.translatable(key);
        g.drawString(font, label, x + 6, y + ROW_H / 2 - font.lineHeight / 2, 0xFFFFFFFF, false);

        int rightEdge = x + w - 4;
        if (reset != null) {
            reset.setX(rightEdge - RESET_W);
            reset.setY(y);
            reset.setWidth(RESET_W);
            reset.setHeight(ROW_H);
            rightEdge -= RESET_W + 4;
        }
        if (control != null) {
            int controlW = Math.min(CONTROL_W, Math.max(60,
                    rightEdge - (x + 6 + font.width(label) + 8)));
            control.setX(rightEdge - controlW);
            control.setY(y);
            control.setWidth(controlW);
            control.setHeight(ROW_H);
        }
        return y + ROW_H + ROW_GAP;
    }

    private void layoutOwnerActions(int sx, int sy, int sw, int sh) {
        int btn = CTRL_BTN;
        int padding = PANEL_PADDING_X;
        int rightEdge = sx + sw - padding;
        int yEdge = sy + sh - padding - btn;

        if (reportButton != null) {
            reportButton.setX(rightEdge - btn);
            reportButton.setY(yEdge);
            reportButton.setWidth(btn);
            reportButton.setHeight(btn);
            rightEdge -= btn + 4;
        }
        if (deleteButton != null) {
            deleteButton.setX(rightEdge - btn);
            deleteButton.setY(yEdge);
            deleteButton.setWidth(btn);
            deleteButton.setHeight(btn);
        }
    }

    private int headerHeight() {
        return PANEL_PADDING_Y + font.lineHeight + 6;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (progress != null && progress.commitDragIfActive()) {
            return true;
        }
        return super.mouseReleased(event);
    }

    private void renderTooltips(GuiGraphics g, int mouseX, int mouseY) {
        com.dreamdisplays.screen.Screen scr = screen;
        if (scr == null || scr.errored) return;

        if (volumeHover != null && volumeHover.contains(mouseX, mouseY)) {
            g.setComponentTooltipForNextFrame(font, List.of(
                    Component.translatable("dreamdisplays.button.volume.tooltip.1").withStyle(s -> s.withColor(ChatFormatting.WHITE).withBold(true)),
                    Component.translatable("dreamdisplays.button.volume.tooltip.2").withStyle(s -> s.withColor(ChatFormatting.GRAY)),
                    Component.translatable("dreamdisplays.button.volume.tooltip.3").withStyle(s -> s.withColor(ChatFormatting.GRAY)),
                    Component.translatable("dreamdisplays.button.volume.tooltip.4",
                            volume != null ? (int) (volume.value * 200) : 0)
                            .withStyle(s -> s.withColor(ChatFormatting.GOLD))
            ), mouseX, mouseY);
        }
        if (renderDHover != null && renderDHover.contains(mouseX, mouseY)) {
            g.setComponentTooltipForNextFrame(font, List.of(
                    Component.translatable("dreamdisplays.button.render-distance.tooltip.1").withStyle(s -> s.withColor(ChatFormatting.WHITE).withBold(true)),
                    Component.translatable("dreamdisplays.button.render-distance.tooltip.2").withStyle(s -> s.withColor(ChatFormatting.GRAY)),
                    Component.translatable("dreamdisplays.button.render-distance.tooltip.3").withStyle(s -> s.withColor(ChatFormatting.GRAY)),
                    Component.translatable("dreamdisplays.button.render-distance.tooltip.8",
                            renderD != null ? (int) (renderD.value * (128 - 24) + 24) : 0)
                            .withStyle(s -> s.withColor(ChatFormatting.GOLD))
            ), mouseX, mouseY);
        }
        if (qualityHover != null && qualityHover.contains(mouseX, mouseY) && quality != null) {
            List<Component> tip = new java.util.ArrayList<>(List.of(
                    Component.translatable("dreamdisplays.button.quality.tooltip.1").withStyle(s -> s.withColor(ChatFormatting.WHITE).withBold(true)),
                    Component.translatable("dreamdisplays.button.quality.tooltip.2").withStyle(s -> s.withColor(ChatFormatting.GRAY)),
                    Component.translatable("dreamdisplays.button.quality.tooltip.4",
                            qualityFromFraction(quality.value))
                            .withStyle(s -> s.withColor(ChatFormatting.GOLD))
            ));
            try {
                if (Integer.parseInt(scr.getQuality()) >= 1080) {
                    tip.add(Component.translatable("dreamdisplays.button.quality.tooltip.5")
                            .withStyle(s -> s.withColor(ChatFormatting.YELLOW)));
                }
            } catch (NumberFormatException ignored) {
            }
            g.setComponentTooltipForNextFrame(font, tip, mouseX, mouseY);
        }
        if (brightnessHover != null && brightnessHover.contains(mouseX, mouseY)) {
            g.setComponentTooltipForNextFrame(font, List.of(
                    Component.translatable("dreamdisplays.button.brightness.tooltip.1").withStyle(s -> s.withColor(ChatFormatting.WHITE).withBold(true)),
                    Component.translatable("dreamdisplays.button.brightness.tooltip.2").withStyle(s -> s.withColor(ChatFormatting.GRAY)),
                    Component.translatable("dreamdisplays.button.brightness.tooltip.3",
                            brightness != null ? (int) Math.floor(brightness.value * 200) : 100)
                            .withStyle(s -> s.withColor(ChatFormatting.GOLD))
            ), mouseX, mouseY);
        }
        if (syncHover != null && syncHover.contains(mouseX, mouseY) && sync != null) {
            g.setComponentTooltipForNextFrame(font, List.of(
                    Component.translatable("dreamdisplays.button.synchronization.tooltip.1").withStyle(s -> s.withColor(ChatFormatting.WHITE).withBold(true)),
                    Component.translatable("dreamdisplays.button.synchronization.tooltip.2").withStyle(s -> s.withColor(ChatFormatting.GRAY)),
                    Component.translatable("dreamdisplays.button.synchronization.tooltip.3").withStyle(s -> s.withColor(ChatFormatting.GRAY)),
                    Component.translatable("dreamdisplays.button.synchronization.tooltip.5",
                            sync.value
                                    ? Component.translatable("dreamdisplays.button.enabled")
                                    : Component.translatable("dreamdisplays.button.disabled"))
                            .withStyle(s -> s.withColor(ChatFormatting.GOLD))
            ), mouseX, mouseY);
        }

        if (deleteButton != null && hovered(mouseX, mouseY, deleteButton)) {
            g.setComponentTooltipForNextFrame(font, List.of(
                    Component.translatable("dreamdisplays.button.delete.tooltip.1").withStyle(s -> s.withColor(ChatFormatting.WHITE).withBold(true)),
                    Component.translatable("dreamdisplays.button.delete.tooltip.2").withStyle(s -> s.withColor(ChatFormatting.GRAY))
            ), mouseX, mouseY);
        }
        if (reportButton != null && hovered(mouseX, mouseY, reportButton)) {
            g.setComponentTooltipForNextFrame(font, List.of(
                    Component.translatable("dreamdisplays.button.report.tooltip.1").withStyle(s -> s.withColor(ChatFormatting.WHITE).withBold(true)),
                    Component.translatable("dreamdisplays.button.report.tooltip.2").withStyle(s -> s.withColor(ChatFormatting.GRAY))
            ), mouseX, mouseY);
        }
    }

    private static boolean hovered(int mx, int my, AbstractWidget w) {
        return mx >= w.getX() && mx < w.getX() + w.getWidth()
                && my >= w.getY() && my < w.getY() + w.getHeight();
    }

    private void drawPanel(GuiGraphics g, int x, int y, int w, int h, String title) {
        g.fill(x, y, x + w, y + h, PANEL_BG);
        int b = PANEL_BORDER;
        g.fill(x, y, x + w, y + 1, b);
        g.fill(x, y + h - 1, x + w, y + h, b);
        g.fill(x, y, x + 1, y + h, b);
        g.fill(x + w - 1, y, x + w, y + h, b);
        g.drawString(font, title, x + PANEL_PADDING_X, y + PANEL_PADDING_Y, 0xFFFFFFFF, false);
    }

    private double qualityFraction(String q) {
        if (screen == null) return 0;
        List<Integer> list = screen.getQualityList();
        if (list.isEmpty()) return 0;
        int target;
        try {
            target = Integer.parseInt(q.replace("p", ""));
        } catch (Exception e) {
            target = 720;
        }
        int closest = list.get(0);
        int minDiff = Math.abs(target - closest);
        for (int v : list) {
            int d = Math.abs(target - v);
            if (d < minDiff) {
                minDiff = d;
                closest = v;
            }
        }
        return list.indexOf(closest) / (double) Math.max(1, list.size() - 1);
    }

    private String qualityFromFraction(double v) {
        if (screen == null) return "720";
        List<Integer> list = screen.getQualityList();
        if (list.isEmpty()) return "144";
        int idx = (int) Math.round(v * (list.size() - 1));
        idx = Math.max(0, Math.min(list.size() - 1, idx));
        return String.valueOf(list.get(idx));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record HoverArea(int x, int y, int w, int h) {
        boolean contains(int mx, int my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private static final String GITHUB_URL = "https://github.com/arsmotorin/dreamdisplays";
    private @Nullable HoverArea modLabelHover;
    private long modLabelOpenedAtMs = System.currentTimeMillis();

    private void renderModLabel(GuiGraphics g, int x, int y) {
        boolean update = UpdateCheck.shouldShowArrow();
        Component name = Component.literal("Dream Displays");
        Component ver = Component.literal(" v" + com.dreamdisplays.util.Utils.getModVersion())
                .withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(0xFF6AB7FF));

        int yOffset = 0;
        if (update) {
            float t = ((System.currentTimeMillis() - modLabelOpenedAtMs) % 1800L) / 1800F;
            if (t < 0.25F) {
                float p = t / 0.25F;
                yOffset = (int) (-Math.sin(p * Math.PI) * 3F);
            }
        }
        Component label = update
                ? name.copy().append(ver).append(Component.literal(" ▲")
                        .withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(0xFFFFD55A)))
                : name.copy().append(ver);
        g.drawString(font, label, x, y + yOffset, 0xFFFFFFFF, true);
        modLabelHover = new HoverArea(x, y + yOffset - 1, font.width(label), font.lineHeight + 2);
    }
}

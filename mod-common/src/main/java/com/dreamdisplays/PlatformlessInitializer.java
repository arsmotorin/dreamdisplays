package com.dreamdisplays;

import com.dreamdisplays.net.*;
import me.inotsleep.utils.logging.LoggingManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.BlockHitResult;
import org.joml.Vector3i;
import org.lwjgl.glfw.GLFW;
import org.slf4j.LoggerFactory;
import com.dreamdisplays.downloader.GstreamerDownloadInit;
import com.dreamdisplays.net.*;
import com.dreamdisplays.screen.DisplayConfScreen;
import com.dreamdisplays.screen.Screen;
import com.dreamdisplays.screen.ScreenManager;
import com.dreamdisplays.util.Facing;
import com.dreamdisplays.util.RCUtil;
import com.dreamdisplays.util.Utils;

import java.io.File;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class PlatformlessInitializer {

    public static Config config;

    public static Thread timerThread = new Thread(() -> {
        int lastDistance = 64;
        boolean isErrored = false;
        while (!isErrored) {
            ScreenManager.getScreens().forEach(Screen::reloadQuality);
            if (
                    config.defaultDistance != lastDistance
            ) {
                config.defaultDistance = lastDistance;
                config.save();
            }
            try {
                Thread.sleep(2500);
            } catch (InterruptedException e) {
                isErrored = true;
            }
        }
    });

    public static boolean isOnScreen = false;
    public static boolean focusMode = false;

    public static boolean displaysEnabled = true;

    public static Config getConfig() {
        return config;
    }

    public static final String MOD_ID = "dreamdisplays";

    private static Screen hoveredScreen = null;

    private static Mod mod;

    public static void onModInit(Mod DreamDisplaysMod) {
        mod = DreamDisplaysMod;
        LoggingManager.setLogger(LoggerFactory.getLogger(MOD_ID));
        LoggingManager.info("Starting Dream Displays");

        config = new Config(new File("./config/" + MOD_ID));
        config.reload();

        GstreamerDownloadInit.init();
        new WindowFocusMuteThread().start();

        timerThread.start();
    }

    public static void onDisplayInfoPacket(DisplayInfoPacket packet) {
        if (!PlatformlessInitializer.displaysEnabled) return;

        if (ScreenManager.screens.containsKey(packet.id())) {
            Screen screen = ScreenManager.screens.get(packet.id());
            screen.updateData(packet);
            return;
        }

        createScreen(packet.id(), packet.ownerId(), packet.pos(), packet.facing(), packet.width(), packet.height(), packet.url(), packet.lang(), packet.isSync());
    }

    public static void createScreen(UUID id, UUID ownerId, Vector3i pos, Facing facing, int width, int height, String code, String lang, boolean isSync) {
        Screen screen = new Screen(id, ownerId, pos.x(), pos.y(), pos.z(), facing.toString(), width, height, isSync);
        assert Minecraft.getInstance().player != null;
        if (screen.getDistanceToScreen(Minecraft.getInstance().player.blockPosition()) > PlatformlessInitializer.config.defaultDistance) return;
        ScreenManager.registerScreen(screen);
        if (!Objects.equals(code, "")) screen.loadVideo(code, lang);
    }

    public static void onSyncPacket(SyncPacket packet) {
        if (!ScreenManager.screens.containsKey(packet.id())) return;
        Screen screen = ScreenManager.screens.get(packet.id());
        screen.updateData(packet);
    }

    private static final boolean[] wasPressed = {false};
    private static final AtomicBoolean wasInMultiplayer = new AtomicBoolean(false);
    private static final AtomicReference<ClientLevel> lastLevel = new AtomicReference<>(null);
    private static final AtomicBoolean wasFocused = new AtomicBoolean(false);

    private static void checkVersionAndSendPacket() {
        try {
            String version = Utils.readResource("/version");
            sendPacket(new VersionPacket(version));
        } catch (Exception e) {
            LoggingManager.error("Unable to get version", e);
        }
    }

    public static void onEndTick(Minecraft minecraft) {
        if (minecraft.level != null && minecraft.getCurrentServer() != null) {
            if (lastLevel.get() == null) {
                lastLevel.set(minecraft.level);
                checkVersionAndSendPacket();
            }

            if (minecraft.level != lastLevel.get()) {
                lastLevel.set(minecraft.level);

                ScreenManager.unloadAll();
                hoveredScreen = null;

                checkVersionAndSendPacket();

            }

            wasInMultiplayer.set(true);
        } else {
            if (wasInMultiplayer.get()) {
                wasInMultiplayer.set(false);
                ScreenManager.unloadAll();
                hoveredScreen = null;
                lastLevel.set(null);
                return;
            }
        }

        if (minecraft.player == null) return;

        BlockHitResult result = RCUtil.rCBlock(64);
        hoveredScreen = null;
        PlatformlessInitializer.isOnScreen = false;

        for (Screen screen : ScreenManager.getScreens()) {
            if (PlatformlessInitializer.config.defaultDistance < screen.getDistanceToScreen(minecraft.player.blockPosition()) || !PlatformlessInitializer.displaysEnabled) {
                ScreenManager.unregisterScreen(screen);
                if (hoveredScreen == screen) {
                    hoveredScreen = null;
                    PlatformlessInitializer.isOnScreen = false;
                }
            } else {
                if (result != null) if (screen.isInScreen(result.getBlockPos())) {
                    hoveredScreen = screen;
                    PlatformlessInitializer.isOnScreen = true;
                }

                screen.tick(minecraft.player.blockPosition());
            }
        }

        long window = minecraft.getWindow().handle();
        boolean pressed = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;

        if (pressed && !wasPressed[0]) {
            if (minecraft.player != null && minecraft.player.isShiftKeyDown()) {
                checkAndOpenScreen();
            }
        }

        wasPressed[0] = pressed;

        if (PlatformlessInitializer.focusMode && minecraft.player != null && hoveredScreen != null) {
            minecraft.player.addEffect(new MobEffectInstance(
                    MobEffects.BLINDNESS,
                    20 * 2,
                    1,
                    false,
                    false,
                    false
            ));

            wasFocused.set(true);

        } else if (!PlatformlessInitializer.focusMode && wasFocused.get() && minecraft.player != null) {
            minecraft.player.removeEffect(MobEffects.BLINDNESS);
            wasFocused.set(false);
        }
    }

    private static void checkAndOpenScreen() {
        if (hoveredScreen == null) return;
        DisplayConfScreen.open(hoveredScreen);
    }

    public static void sendPacket(CustomPacketPayload packet) {
        mod.sendPacket(packet);
    }

    public static void onDeletePacket(DeletePacket deletePacket) {
        Screen screen = ScreenManager.screens.get(deletePacket.id());
        if (screen == null) return;

        ScreenManager.unregisterScreen(screen);
    }

    public static void onStop() {
        timerThread.interrupt();
        ScreenManager.unloadAll();
        WindowFocusMuteThread.instance.interrupt();
    }

    public static boolean isPremium = false;

    public static void onPremiumPacket(PremiumPacket packet) {
        isPremium = packet.premium();
    }
}
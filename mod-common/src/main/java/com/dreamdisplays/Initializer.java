package com.dreamdisplays;

import com.dreamdisplays.net.*;
import com.dreamdisplays.screen.Menu;
import com.dreamdisplays.screen.Manager;
import com.dreamdisplays.screen.Screen;
import com.dreamdisplays.screen.Settings;
import com.dreamdisplays.util.Facing;
import com.dreamdisplays.util.RayCasting;
import com.dreamdisplays.util.Utils;
import me.inotsleep.utils.logging.LoggingManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.BlockHitResult;
import org.joml.Vector3i;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@NullMarked
public class Initializer {

    public static final String MOD_ID = "dreamdisplays";
    private static final boolean[] wasPressed = {false};
    private static final AtomicBoolean wasInMultiplayer = new AtomicBoolean(false);
    private static final AtomicReference<@Nullable ClientLevel> lastLevel = new AtomicReference<>(null);
    private static final AtomicBoolean wasFocused = new AtomicBoolean(false);
    public static Config config = new Config(new File("./config/" + MOD_ID));
    public static Thread timerThread = new Thread(() -> {
        int lastDistance = 64;
        boolean isErrored = false;
        while (!isErrored) {
            Manager.getScreens().forEach(Screen::reloadQuality);
            if (config.defaultDistance != lastDistance) {
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
    public static boolean isPremium = false;
    private static @Nullable Screen hoveredScreen = null;
    private static Mod mod;

    public static Config getConfig() {
        return config;
    }

    public static void onModInit(Mod DreamDisplaysMod) {
        mod = DreamDisplaysMod;
        LoggingManager.setLogger(LoggerFactory.getLogger(MOD_ID));
        LoggingManager.info("Starting Dream Displays");
        config.reload();

        // Load client display settings
        Settings.load();

        // Initialize FFmpeg native libraries
        try {
            LoggingManager.info("Loading FFmpeg native libraries...");
            org.bytedeco.javacpp.Loader.load(org.bytedeco.ffmpeg.global.avutil.class);
            org.bytedeco.javacpp.Loader.load(org.bytedeco.ffmpeg.global.avcodec.class);
            org.bytedeco.javacpp.Loader.load(org.bytedeco.ffmpeg.global.avformat.class);
            org.bytedeco.javacpp.Loader.load(org.bytedeco.ffmpeg.global.swscale.class);
            org.bytedeco.javacpp.Loader.load(org.bytedeco.ffmpeg.global.swresample.class);
            LoggingManager.info("FFmpeg libraries loaded successfully!");
        } catch (Exception e) {
            LoggingManager.error("Failed to load FFmpeg libraries", e);
        }

        new Focuser().start();

        timerThread.start();
    }

    public static void onDisplayInfoPacket(Info packet) {
        if (!Initializer.displaysEnabled) return;

        if (Manager.screens.containsKey(packet.id())) {
            Screen screen = Manager.screens.get(packet.id());
            screen.updateData(packet);
            return;
        }

        createScreen(packet.id(), packet.ownerId(), packet.pos(), packet.facing(), packet.width(), packet.height(), packet.url(), packet.lang(), packet.isSync());
    }

    public static void createScreen(UUID id, UUID ownerId, Vector3i pos, Facing facing, int width, int height, String code, String lang, boolean isSync) {
        Screen screen = new Screen(id, ownerId, pos.x(), pos.y(), pos.z(), facing.toString(), width, height, isSync);
        assert Minecraft.getInstance().player != null;
        if (screen.getDistanceToScreen(Minecraft.getInstance().player.blockPosition()) > Initializer.config.defaultDistance)
            return;
        Manager.registerScreen(screen);
        if (!Objects.equals(code, "")) screen.loadVideo(code, lang);
    }

    public static void onSyncPacket(Sync packet) {
        if (!Manager.screens.containsKey(packet.id())) return;
        Screen screen = Manager.screens.get(packet.id());
        if (screen != null) {
            screen.updateData(packet);
        }
    }

    private static void checkVersionAndSendPacket() {
        try {
            String version = Utils.getModVersion();
            sendPacket(new Version(version));
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

                Manager.unloadAll();
                hoveredScreen = null;

                checkVersionAndSendPacket();

            }

            wasInMultiplayer.set(true);
        } else {
            if (wasInMultiplayer.get()) {
                wasInMultiplayer.set(false);
                Manager.unloadAll();
                hoveredScreen = null;
                lastLevel.set(null);
                return;
            }
        }

        if (minecraft.player == null) return;

        BlockHitResult result = RayCasting.rCBlock(64);
        hoveredScreen = null;
        Initializer.isOnScreen = false;

        for (Screen screen : Manager.getScreens()) {
            if (Initializer.config.defaultDistance < screen.getDistanceToScreen(minecraft.player.blockPosition()) || !Initializer.displaysEnabled) {
                Manager.unregisterScreen(screen);
                if (hoveredScreen == screen) {
                    hoveredScreen = null;
                    Initializer.isOnScreen = false;
                }
            } else {
                if (result != null) if (screen.isInScreen(result.getBlockPos())) {
                    hoveredScreen = screen;
                    Initializer.isOnScreen = true;
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

        if (Initializer.focusMode && minecraft.player != null && hoveredScreen != null) {
            minecraft.player.addEffect(new MobEffectInstance(
                MobEffects.BLINDNESS,
                20 * 2,
                1,
                false,
                false,
                false
            ));

            wasFocused.set(true);

        } else if (!Initializer.focusMode && wasFocused.get() && minecraft.player != null) {
            minecraft.player.removeEffect(MobEffects.BLINDNESS);
            wasFocused.set(false);
        }
    }

    private static void checkAndOpenScreen() {
        if (hoveredScreen == null) return;
        Menu.open(hoveredScreen);
    }

    public static void sendPacket(CustomPacketPayload packet) {
        mod.sendPacket(packet);
    }

    public static void onDeletePacket(Delete deletePacket) {
        Screen screen = Manager.screens.get(deletePacket.id());
        if (screen == null) return;

        Manager.unregisterScreen(screen);
    }

    public static void onStop() {
        timerThread.interrupt();
        Manager.unloadAll();
        Focuser.instance.interrupt();
    }

    public static void onPremiumPacket(Premium packet) {
        isPremium = packet.premium();
    }
}

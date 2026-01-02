package com.dreamdisplays;

import com.dreamdisplays.downloader.Init;
import com.dreamdisplays.net.Packets.*;
import com.dreamdisplays.screen.Configuration;
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
import net.minecraft.world.entity.player.Player;
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

/**
 * Main initializer.
 */
@NullMarked
public class Initializer {

    public static final String MOD_ID = "dreamdisplays";
    private static final boolean[] wasPressed = {false};
    private static final AtomicBoolean wasInMultiplayer = new AtomicBoolean(
            false
    );
    private static final AtomicReference<@Nullable ClientLevel> lastLevel =
            new AtomicReference<>(null);
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
    public static boolean isReportingEnabled = true;
    private static @Nullable Screen hoveredScreen = null;
    private static Mod mod;

    public static Config getConfig() {
        return config;
    }

    public static void onModInit(Mod dreamDisplaysMod) {
        mod = dreamDisplaysMod;
        LoggingManager.setLogger(LoggerFactory.getLogger(MOD_ID));
        LoggingManager.info("Starting Dream Displays...");
        config.reload();

        // Load client display settings
        Settings.load();

        Init.init();
        new Focuser().start();

        timerThread.start();
    }

    public static void onDisplayInfoPacket(Info packet) {
        if (!Initializer.displaysEnabled) return;

        if (Manager.screens.containsKey(packet.uuid())) {
            Screen screen = Manager.screens.get(packet.uuid());
            screen.updateData(packet);
            return;
        }

        createScreen(
                packet.uuid(),
                packet.ownerUuid(),
                packet.pos(),
                packet.facing(),
                packet.width(),
                packet.height(),
                packet.url(),
                packet.lang(),
                packet.isSync()
        );
    }

    public static void onDisplayEnabledPacket(DisplayEnabled packet) {
        Initializer.displaysEnabled = packet.enabled();
        config.displaysEnabled = packet.enabled();
        config.save();
    }

    public static void createScreen(
            UUID uuid,
            UUID ownerUuid,
            Vector3i pos,
            Facing facing,
            int width,
            int height,
            String code,
            String lang,
            boolean isSync
    ) {
        Screen screen = new Screen(
                uuid,
                ownerUuid,
                pos.x(),
                pos.y(),
                pos.z(),
                facing.toString(),
                width,
                height,
                isSync
        );

//        Settings.FullDisplayData savedData = Settings.getDisplayData(uuid);
//        int renderDistance = savedData != null ? savedData.renderDistance : config.defaultDistance;
//        screen.setRenderDistance(renderDistance);

        Player player = Minecraft.getInstance().player;
        if (
                player != null &&
                        screen.getDistanceToScreen(player.blockPosition()) >
                                Initializer.config.defaultDistance
        ) return;
        Manager.registerScreen(screen);
        if (!Objects.equals(code, "")) screen.loadVideo(code, lang);
    }

    public static void onSyncPacket(Sync packet) {
        if (!Manager.screens.containsKey(packet.uuid())) return;
        Screen screen = Manager.screens.get(packet.uuid());
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
        ClientLevel level = minecraft.level;
        if (level != null && minecraft.getCurrentServer() != null) {
            if (lastLevel.get() == null) {
                lastLevel.set(level);
                checkVersionAndSendPacket();
            }

            if (level != lastLevel.get()) {
                lastLevel.set(level);

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

        BlockHitResult result = RayCasting.rCBlock(64);
        hoveredScreen = null;
        Initializer.isOnScreen = false;
        Player player = minecraft.player;
        if (player == null) return;
        for (Screen screen : Manager.getScreens()) {
            double displayRenderDistance = screen.getRenderDistance();

            if (
                    displayRenderDistance <
                            screen.getDistanceToScreen(player.blockPosition()) ||
                            !Initializer.displaysEnabled
            ) {
                Manager.saveScreenData(screen);
                Manager.unregisterScreen(screen);
                if (hoveredScreen == screen) {
                    hoveredScreen = null;
                    Initializer.isOnScreen = false;
                }
            } else {
                if (result != null) if (
                        screen.isInScreen(result.getBlockPos())
                ) {
                    hoveredScreen = screen;
                    Initializer.isOnScreen = true;
                }

                screen.tick(player.blockPosition());
            }
        }

        long window = minecraft.getWindow().handle();
        boolean pressed =
                GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) ==
                        GLFW.GLFW_PRESS;

        if (pressed && !wasPressed[0]) {
            if (player.isShiftKeyDown()) {
                checkAndOpenScreen();
            }
        }

        wasPressed[0] = pressed;

        if (Initializer.focusMode && hoveredScreen != null) {
            player.addEffect(
                    new MobEffectInstance(
                            MobEffects.BLINDNESS,
                            20 * 2,
                            1,
                            false,
                            false,
                            false
                    )
            );

            wasFocused.set(true);
        } else if (!Initializer.focusMode && wasFocused.get()) {
            player.removeEffect(MobEffects.BLINDNESS);
            wasFocused.set(false);
        }
    }

    private static void checkAndOpenScreen() {
        if (hoveredScreen == null) return;
        Configuration.open(hoveredScreen);
    }

    public static void sendPacket(CustomPacketPayload packet) {
        mod.sendPacket(packet);
    }

    public static void onDeletePacket(Delete packet) {
        Screen screen = Manager.screens.get(packet.uuid());
        if (screen != null) {
            Manager.unregisterScreen(screen);
        }

        Settings.removeDisplay(packet.uuid());
        LoggingManager.info(
                "Display deleted and removed from saved data: " + packet.uuid()
        );
    }

    public static void onStop() {
        Manager.saveAllScreens();
        timerThread.interrupt();
        Manager.unloadAll();
        Focuser.instance.interrupt();
    }

    public static void onPremiumPacket(Premium packet) {
        isPremium = packet.premium();
    }

    public static void onReportEnabledPacket(ReportEnabled packet) {
        isReportingEnabled = packet.enabled();
    }
}

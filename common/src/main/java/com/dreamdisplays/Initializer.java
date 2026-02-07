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
import net.minecraft.core.BlockPos;
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
    private static int unloadCheckTick = 0;
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

        // Check if player is in range before creating the display
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            // Get saved render distance or use default
            Settings.FullDisplayData savedData = Settings.getDisplayData(packet.uuid());
            int renderDistance = savedData != null ? savedData.renderDistance : config.defaultDistance;

            // Screen.getDistanceToScreen()
            int x = packet.pos().x;
            int y = packet.pos().y;
            int z = packet.pos().z;
            int width = packet.width();
            int height = packet.height();
            String facing = packet.facing().toString();

            int maxX = x;
            int maxY = y + height - 1;
            int maxZ = z;

            switch (facing) {
                case "NORTH", "SOUTH" -> maxX += width - 1;
                case "EAST", "WEST" -> maxZ += width - 1;
            }

            BlockPos playerPos = player.blockPosition();
            int clampedX = Math.min(Math.max(playerPos.getX(), x), maxX);
            int clampedY = Math.min(Math.max(playerPos.getY(), y), maxY);
            int clampedZ = Math.min(Math.max(playerPos.getZ(), z), maxZ);

            BlockPos closestPos = new BlockPos(clampedX, clampedY, clampedZ);
            double distance = Math.sqrt(playerPos.distSqr(closestPos));

            // Only create if within render distance
            if (distance > renderDistance) {
                return;
            }
        }

        Manager.unloadedScreens.remove(packet.uuid());

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

        Settings.FullDisplayData savedData = Settings.getDisplayData(uuid);
        int renderDistance = savedData != null ? savedData.renderDistance : config.defaultDistance;
        screen.setRenderDistance(renderDistance);

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

    // Restore a screen from cached data (when player enters render distance)
    private static void restoreScreen(Settings.FullDisplayData data) {
        Screen screen = new Screen(
                data.uuid,
                data.ownerUuid,
                data.x,
                data.y,
                data.z,
                data.facing,
                data.width,
                data.height,
                data.isSync
        );

        screen.setRenderDistance(data.renderDistance);
        screen.setSavedTimeNanos(data.currentTimeNanos);
        screen.setVolume(data.volume);
        screen.setQuality(data.quality);
        screen.muted = data.muted;

        Manager.screens.put(screen.getUUID(), screen);

        if (data.videoUrl != null && !data.videoUrl.isEmpty()) {
            screen.loadVideo(data.videoUrl, data.lang != null ? data.lang : "");
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

        unloadCheckTick++;
        if (unloadCheckTick >= 10 && Initializer.displaysEnabled && !Manager.unloadedScreens.isEmpty()) {
            unloadCheckTick = 0;
            // Collect screens to restore first to avoid ConcurrentModificationException
            java.util.List<Settings.FullDisplayData> toRestore = new java.util.ArrayList<>();

            for (Settings.FullDisplayData data : Manager.unloadedScreens.values()) {
                if (data.videoUrl == null || data.videoUrl.isEmpty()) continue;

                // Screen.getDistanceToScreen
                int maxX = data.x;
                int maxY = data.y + data.height - 1;
                int maxZ = data.z;

                switch (data.facing) {
                    case "NORTH", "SOUTH" -> maxX += data.width - 1;
                    case "EAST", "WEST" -> maxZ += data.width - 1;
                }

                BlockPos playerPos = player.blockPosition();
                int clampedX = Math.min(Math.max(playerPos.getX(), data.x), maxX);
                int clampedY = Math.min(Math.max(playerPos.getY(), data.y), maxY);
                int clampedZ = Math.min(Math.max(playerPos.getZ(), data.z), maxZ);

                BlockPos closestPos = new BlockPos(clampedX, clampedY, clampedZ);
                double distance = Math.sqrt(playerPos.distSqr(closestPos));

                // If player is now in range, mark for restoration
                if (distance <= data.renderDistance) {
                    toRestore.add(data);
                }
            }

            // Now restore outside the iteration
            for (Settings.FullDisplayData data : toRestore) {
                Manager.unloadedScreens.remove(data.uuid);
                restoreScreen(data);
            }
        }

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

        Manager.unloadedScreens.remove(packet.uuid());

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

    public static void onClearCachePacket(ClearCache packet) {
        // Remove specific displays from active screens and cache
        for (UUID displayUuid : packet.displayUuids()) {
            Screen screen = Manager.screens.get(displayUuid);
            if (screen != null) {
                screen.unregister();
                Manager.screens.remove(displayUuid);
            }

            // Remove from persistent storage
            Settings.removeDisplay(displayUuid);
        }
    }
}

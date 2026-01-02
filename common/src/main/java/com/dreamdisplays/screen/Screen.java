package com.dreamdisplays.screen;

import com.dreamdisplays.Initializer;
import com.dreamdisplays.net.Packets.Info;
import com.dreamdisplays.net.Packets.RequestSync;
import com.dreamdisplays.net.Packets.Sync;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Represents a video display screen in the game world.
 */
@NullMarked
public class Screen {

    private final UUID uuid;
    private final UUID ownerUuid;
    public boolean owner;
    public boolean errored;
    public boolean isSync;
    public boolean muted;
    public @Nullable DynamicTexture texture = null;
    public @Nullable Identifier textureId = null;
    public @Nullable RenderType renderType = null;
    public int textureWidth = 0;
    public int textureHeight = 0;
    private int x;
    private int y;
    private int z;
    private String facing;
    private int width;
    private int height;
    private float volume;
    private float brightness;
    private boolean videoStarted;
    private boolean paused;
    private String quality;
    private long savedTimeNanos = 0;
    private int renderDistance = 64;
    // Use a combined MediaPlayer instead of the separate VideoDecoder and AudioPlayer.
    private @Nullable MediaPlayer mediaPlayer;
    private @Nullable String videoUrl;
    // Cache (good for performance)
    private transient @Nullable BlockPos blockPos;
    private @Nullable String lang;

    // Constructor for the Screen class
    public Screen(
            UUID uuid,
            UUID ownerUuid,
            int x,
            int y,
            int z,
            String facing,
            int width,
            int height,
            boolean isSync
    ) {
        this.uuid = uuid;
        this.ownerUuid = ownerUuid;
        this.x = x;
        this.y = y;
        this.z = z;
        this.facing = facing;
        this.width = width;
        this.height = height;
        owner =
                Minecraft.getInstance().player != null &&
                        (ownerUuid + "").equals(
                                Minecraft.getInstance().player.getUUID() + ""
                        );

        // Load saved settings for this display
        Settings.DisplaySettings savedSettings = Settings.getSettings(uuid);
        this.volume = savedSettings.volume;
        this.quality = savedSettings.quality;
        this.brightness = savedSettings.brightness;
        this.muted = savedSettings.muted;
        this.paused = savedSettings.paused;

        if (isSync) {
            sendRequestSyncPacket();
        }
    }

    // Creates a custom RenderType for rendering the screen texture
    private static RenderType createRenderType(Identifier id) {
        return RenderType.create(
                "dream-displays",
                RenderSetup.builder(RenderPipelines.SOLID_BLOCK)
                        .withTexture("Sampler0", id)
                        .bufferSize(RenderType.BIG_BUFFER_SIZE)
                        .affectsCrumbling()
                        .useLightmap()
                        .createRenderSetup()
        );
    }

    // Loads a video from a given URL and language
    public void loadVideo(String videoUrl, String lang) {
        if (Objects.equals(videoUrl, "")) return;

        if (mediaPlayer != null) unregister();

        // Load the video URL and language into the screen
        this.videoUrl = videoUrl;
        this.lang = lang;
        boolean shouldBePaused = this.paused;
        CompletableFuture.runAsync(() -> {
            mediaPlayer = new MediaPlayer(videoUrl, lang, this);
            int qualityInt = Integer.parseInt(this.quality.replace("p", ""));
            textureWidth = (int) ((width / (double) height) * qualityInt);
            textureHeight = qualityInt;
        });

        waitForMFInit(() -> {
            startVideo();
            if (shouldBePaused) {
                this.paused = true;
                if (mediaPlayer != null) {
                    mediaPlayer.pause();
                }
            }
        });

        Minecraft.getInstance().execute(this::reloadTexture);
    }

    // Updates the screen data based on a DisplayInfoPacket
    public void updateData(Info packet) {
        this.x = packet.pos().x;
        this.y = packet.pos().y;
        this.z = packet.pos().z;

        this.facing = String.valueOf(packet.facing());

        this.width = packet.width();
        this.height = packet.height();
        this.isSync = packet.isSync();

        owner =
                Minecraft.getInstance().player != null &&
                        (packet.ownerUuid() + "").equals(
                                Minecraft.getInstance().player.getUUID() + ""
                        );

        if (
                !Objects.equals(videoUrl, packet.url()) ||
                        !Objects.equals(lang, packet.lang())
        ) {
            this.paused = false;
            loadVideo(packet.url(), packet.lang());
            if (isSync) {
                sendRequestSyncPacket();
            }
        }
    }

    // Sends a RequestSyncPacket to the server to request synchronization data
    private void sendRequestSyncPacket() {
        Initializer.sendPacket(new RequestSync(uuid));
    }

    // Updates the screen data based on a SyncPacket
    public void updateData(Sync packet) {
        isSync = packet.isSync();
        if (!isSync) return;

        long nanos = System.nanoTime();

        waitForMFInit(() -> {
            if (!videoStarted) {
                startVideo();
                setVolume((float) Initializer.config.syncDisplayVolume);
            }

            if (paused) setPaused(false);
//            if (paused != packet.currentState()) {
//                if (paused) setPaused(false);
//            }

            long lostTime = System.nanoTime() - nanos;
//            long targetTime = packet.currentTime() + lostTime;
//            long currentTime = getCurrentTimeNanos();

            seekVideoTo(packet.currentTime() + lostTime);
            setPaused(packet.currentState());
//            long diff = Math.abs(targetTime - currentTime);
//            if (diff > 2_000_000_000L) {
//                seekVideoTo(targetTime);
//            }
//
//            if (paused != packet.currentState()) {
//                setPaused(packet.currentState());
//            }
        });
    }

    public void reloadTexture() {
        this.createTexture();
    }

    // Reloads the video quality
    public void reloadQuality() {
        if (mediaPlayer != null) {
            mediaPlayer.setQuality(quality);
        }
    }

    // Checks if a given BlockPos is within the screen boundaries
    public boolean isInScreen(BlockPos pos) {
        int maxX = x;
        int maxY = y + height - 1;
        int maxZ = z;

        switch (facing) {
            case "NORTH", "SOUTH" -> maxX += width - 1;
            default -> maxZ += width - 1;
        }

        return (
                x <= pos.getX() &&
                        maxX >= pos.getX() &&
                        y <= pos.getY() &&
                        maxY >= pos.getY() &&
                        z <= pos.getZ() &&
                        maxZ >= pos.getZ()
        );
    }

    // Checks if the video has started playing
    public boolean isVideoStarted() {
        return mediaPlayer != null && mediaPlayer.textureFilled();
    }

    // Calculates the distance from a given BlockPos to the closest point on the screen
    public double getDistanceToScreen(BlockPos pos) {
        int maxX = x;
        int maxY = y + height - 1;
        int maxZ = z;

        switch (facing) {
            case "NORTH", "SOUTH" -> maxX += width - 1;
            case "EAST", "WEST" -> maxZ += width - 1;
        }

        int clampedX = Math.min(Math.max(pos.getX(), x), maxX);
        int clampedY = Math.min(Math.max(pos.getY(), y), maxY);
        int clampedZ = Math.min(Math.max(pos.getZ(), z), maxZ);

        BlockPos closestPos = new BlockPos(clampedX, clampedY, clampedZ);

        return Math.sqrt(pos.distSqr(closestPos));
    }

    // Updates the texture to fit the current video frame
    public void fitTexture() {
        if (mediaPlayer != null && texture != null) {
            try {
                mediaPlayer.updateFrame(texture.getTexture());
            } catch (Exception e) {
                // Ignore errors if texture is not ready
            }
        }
    }

    // Returns screen position as BlockPos
    public BlockPos getPos() {
        if (blockPos == null) {
            blockPos = new BlockPos(x, y, z);
        }
        return blockPos;
    }

    // Returns screen facing direction
    public String getFacing() {
        return facing;
    }

    // Returns screen width
    public float getWidth() {
        return width;
    }

    // Returns screen height
    public float getHeight() {
        return height;
    }

    // Sets video volume
    public void setVideoVolume(float volume) {
        if (mediaPlayer != null) {
            mediaPlayer.setVolume(volume);
        }
    }

    // Returns video quality
    public String getQuality() {
        return quality;
    }

    // Sets video quality (e.g., "480", "720", "1080", "2160")
    public void setQuality(String quality) {
        this.quality = quality;
        if (mediaPlayer != null) {
            mediaPlayer.setQuality(quality);
        }
        // reloadTexture();
        // Save settings
        Settings.updateSettings(uuid, volume, quality, brightness, muted, paused);
    }

    // Returns list of available video qualities
    public List<Integer> getQualityList() {
        if (mediaPlayer == null) return Collections.emptyList();
        return mediaPlayer.getAvailableQualities();
    }

    // Returns video brightness (0.0 to 2.0)
    public float getBrightness() {
        return brightness;
    }

    // Sets video brightness (0.0 to 2.0 - 100%)
    public void setBrightness(float brightness) {
        this.brightness = Math.max(0, Math.min(2, brightness));
        if (mediaPlayer != null) {
            mediaPlayer.setBrightness(this.brightness);
        }
        // Save settings
        Settings.updateSettings(uuid, volume, quality, this.brightness, muted, paused);
    }

    // Starts video playback
    public void startVideo() {
        if (mediaPlayer != null) {
            videoStarted = true;
            if (paused) {
                mediaPlayer.pause();
            } else {
                mediaPlayer.play();
                paused = false;
            }
            restoreSavedTime();
        }
    }

    // Returns the paused state of the video
    public boolean getPaused() {
        return paused;
    }

    // Sets the paused state of the video
    public void setPaused(boolean paused) {
        if (!videoStarted) {
            this.paused = paused;
            waitForMFInit(() -> {
                startVideo();
                setVolume((float) Initializer.config.defaultDisplayVolume);
            });
            return;
        }
        this.paused = paused;
        if (mediaPlayer != null) {
            if (paused) {
                mediaPlayer.pause();
            } else {
                mediaPlayer.play();
            }
        }
        Settings.updateSettings(uuid, volume, quality, brightness, muted, paused);
        if (owner && isSync) sendSync();
    }

    // Relative seek video: moves the video by a specified number of seconds (in our case it's +5 seconds) relative to the current position
    public void seekForward() {
        seekVideoRelative(5);
    }

    //  Relative seek video: moves the video by a specified number of seconds (in our case it's -5 seconds) relative to the current position
    public void seekBackward() {
        seekVideoRelative(-5);
    }

    // Relative seek video: moves the video by a specified number of seconds relative to the current position
    public void seekVideoRelative(long seconds) {
        if (mediaPlayer != null) {
            mediaPlayer.seekRelative(seconds);
        }
    }

    // Absolute (cinema) seek video: moves to a specific second
    public void seekVideoTo(long nanos) {
        if (mediaPlayer != null) {
            mediaPlayer.seekTo(nanos, false);
        }
    }

    public void unregister() {
        if (mediaPlayer != null) mediaPlayer.stop();

        // Schedule texture cleanup on render thread to avoid "Rendersystem called from wrong thread" error
        Minecraft minecraft = getMinecraft();

        if (minecraft.screen instanceof Configuration displayConfScreen) {
            if (displayConfScreen.screen == this) displayConfScreen.onClose();
        }
    }

    private Minecraft getMinecraft() {
        Minecraft minecraft = Minecraft.getInstance();
        if (textureId != null) {
            minecraft.execute(() -> {
                TextureManager manager = minecraft.getTextureManager();
                if (textureId != null) {
                    try {
                        manager.release(textureId);
                    } catch (Exception ignored) {
                    }
                }
            });
        }
        return minecraft;
    }

    public UUID getUUID() {
        return uuid;
    }

    public void mute(boolean status) {
        if (muted == status) return;
        muted = status;

        setVideoVolume(!status ? volume : 0);
        Settings.updateSettings(uuid, volume, quality, brightness, muted, paused);
    }

    public double getVolume() {
        return volume;
    }

    // Sets video volume (0.0 to 1.0)
    public void setVolume(float volume) {
        this.volume = volume;
        setVideoVolume(volume);
        Settings.updateSettings(uuid, volume, quality, brightness, muted, paused);
    }

    // Creates a new texture for the screen based on its dimensions and quality
    public void createTexture() {
        int qualityInt = Integer.parseInt(this.quality.replace("p", ""));
        textureWidth = (int) ((width / (double) height) * qualityInt);
        textureHeight = qualityInt;

        if (texture != null) {
            texture.close();
            if (textureId != null) Minecraft.getInstance()
                    .getTextureManager()
                    .release(textureId);
        }
        texture = new DynamicTexture(
                UUID.randomUUID().toString(),
                textureWidth,
                textureHeight,
                true
        );
        textureId = Identifier.fromNamespaceAndPath(
                Initializer.MOD_ID,
                "screen-main-texture-" + uuid + "-" + UUID.randomUUID()
        );

        Minecraft.getInstance()
                .getTextureManager()
                .register(textureId, texture);
        renderType = createRenderType(textureId);
    }

    public void sendSync() {
        if (mediaPlayer != null) {
            Initializer.sendPacket(
                    new Sync(
                            uuid,
                            isSync,
                            paused,
                            mediaPlayer.getCurrentTime(),
                            mediaPlayer.getDuration()
                    )
            );
        }
    }

    public long getCurrentTimeNanos() {
        if (mediaPlayer != null) {
            return mediaPlayer.getCurrentTime();
        }
        return 0;
    }

    public int getRenderDistance() {
        return renderDistance;
    }

    public void setRenderDistance(int distance) {
        this.renderDistance = distance;
    }

    // Set the saved time to restore when video loads
    public void setSavedTimeNanos(long timeNanos) {
        this.savedTimeNanos = timeNanos;
    }

    // Restore the saved video playback time
    public void restoreSavedTime() {
        if (
                savedTimeNanos > 0 &&
                        mediaPlayer != null &&
                        mediaPlayer.isInitialized()
        ) {
            mediaPlayer.seekTo(savedTimeNanos, false);
        }
    }

    public void waitForMFInit(Runnable action) {
        new Thread(() -> {
            while (mediaPlayer == null || !mediaPlayer.isInitialized()) {
                try {
                    Thread.sleep(100); // TODO: this is ugly
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            action.run();
        })
                .start();
    }

    public @Nullable String getVideoUrl() {
        return videoUrl;
    }

    public @Nullable String getLang() {
        return lang;
    }

    public @Nullable UUID getOwnerUuid() {
        return ownerUuid;
    }

    public void tick(BlockPos pos) {
        if (mediaPlayer != null) mediaPlayer.tick(
                pos,
                Initializer.config.defaultDistance
        );
    }

    public void afterSeek() {
        if (owner && isSync) sendSync();
    }
}

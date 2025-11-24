package com.dreamdisplays.screen;

import me.inotsleep.utils.logging.LoggingManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import com.dreamdisplays.Initializer;
import com.dreamdisplays.net.Info;
import com.dreamdisplays.net.RequestSync;
import com.dreamdisplays.net.Sync;
import com.dreamdisplays.util.Image;
import com.dreamdisplays.util.Utils;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@NullMarked
public class Screen {
    public boolean owner;

    public boolean errored;

    private int x;
    private int y;
    private int z;
    private String facing;
    private int width;
    private int height;
    private final UUID id;
    private float volume;
    private boolean videoStarted;
    private boolean paused;
    private String quality = "720";
    public boolean isSync;
    public boolean muted;

    // Use a combined MediaPlayer instead of the separate VideoDecoder and AudioPlayer.
    private @Nullable MediaPlayer mediaPlayer;

    private @Nullable String videoUrl;

    public @Nullable DynamicTexture texture = null;
    public @Nullable Identifier textureId = null;
    public @Nullable RenderType renderType = null;

    public int textureWidth = 0;
    public int textureHeight = 0;

    // Cache (good for performance)
    private transient @Nullable BlockPos blockPos;

    private @Nullable DynamicTexture previewTexture = null;
    public @Nullable Identifier previewTextureId = null;
    public @Nullable RenderType previewRenderType = null;
    private @Nullable String lang;

    // Constructor for the Screen class
    public Screen(UUID id, UUID ownerId, int x, int y, int z, String facing, int width, int height, boolean isSync) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.z = z;
        this.facing = facing;
        this.width = width;
        this.height = height;
        owner = Minecraft.getInstance().player != null && (ownerId + "").equals(Minecraft.getInstance().player.getUUID() + "");

        // Load saved settings for this display
        Settings.DisplaySettings savedSettings = Settings.getSettings(id);
        this.volume = savedSettings.volume;
        this.quality = savedSettings.quality;
        this.muted = savedSettings.muted;

        if (isSync) {
            sendRequestSyncPacket();
        }
    }

    // Loads a video from a given URL and language
    public void loadVideo(String videoUrl, String lang) {
        if (Objects.equals(videoUrl, "")) return;

        LoggingManager.info("Loading video: " + videoUrl);

        if (mediaPlayer != null) unregister();

        // Load the video URL and language into the screen.
        this.videoUrl = videoUrl;
        this.lang = lang;
        CompletableFuture.runAsync(() -> {
            this.videoUrl = videoUrl;
            mediaPlayer = new MediaPlayer(videoUrl, lang, this);
            int qualityInt = Integer.parseInt(this.quality.replace("p", ""));
            textureWidth = (int) (width / (double) height * qualityInt);
            textureHeight = qualityInt;

            // TODO: note for INotSleep: we should delete video previews to avoid problems with videos
            Image.fetchImageTextureFromUrl("https://img.youtube.com/vi/" + Utils.extractVideoId(videoUrl) + "/maxresdefault.jpg")
                    .thenAcceptAsync(nativeImageBackedTexture -> {
                        previewTexture = nativeImageBackedTexture;
                        previewTextureId = Identifier.fromNamespaceAndPath(Initializer.MOD_ID, "screen-preview-"+id+"-"+UUID.randomUUID());

                        if (previewTexture != null) {
                            Minecraft.getInstance().getTextureManager().register(previewTextureId, previewTexture);
                            previewRenderType = createRenderType(previewTextureId);
                        }
                    });
        });

        waitForMFInit(this::startVideo);

        Minecraft.getInstance().execute(this::reloadTexture);
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
    // Updates the screen data based on a DisplayInfoPacket
    public void updateData(Info packet) {
        this.x = packet.pos().x;
        this.y = packet.pos().y;
        this.z = packet.pos().z;

        this.facing = String.valueOf(packet.facing());

        this.width = packet.width();
        this.height = packet.height();
        this.isSync = packet.isSync();

        owner = Minecraft.getInstance().player != null && (packet.ownerId() + "").equals(Minecraft.getInstance().player.getUUID() + "");

        if (!Objects.equals(videoUrl, packet.url()) || !Objects.equals(lang, packet.lang())) {
            loadVideo(packet.url(), packet.lang());
            if (isSync) {
                sendRequestSyncPacket();
            }
        }
    }

    // Sends a RequestSyncPacket to the server to request synchronization data
    private void sendRequestSyncPacket() {
        Initializer.sendPacket(new RequestSync(id));
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

            long lostTime = System.nanoTime() - nanos;

            seekVideoTo(packet.currentTime() + lostTime);
            setPaused(packet.currentState());
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

        return x <= pos.getX() && maxX >= pos.getX() &&
                y <= pos.getY() && maxY >= pos.getY() &&
                z <= pos.getZ() && maxZ >= pos.getZ();
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

        BlockPos closestPoint = new BlockPos(clampedX, clampedY, clampedZ);

        return Math.sqrt(pos.distSqr(closestPoint));
    }

    // Updates the texture to fit the current video frame
    public void fitTexture() {
        if (mediaPlayer != null && texture != null) {
            mediaPlayer.updateFrame(texture.getTexture());
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

    // Sets video volume (0.0 to 1.0)
    public void setVolume(float volume) {
        this.volume = volume;
        setVideoVolume(volume);
        // Save settings
        Settings.updateSettings(id, volume, quality, muted);
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

    // Returns list of available video qualities
    public List<Integer> getQualityList() {
        if (mediaPlayer == null) return Collections.emptyList();
        return mediaPlayer.getAvailableQualities();
    }

    // Sets video quality (e.g., "480", "720", "1080", "2160")
    public void setQuality(String quality) {
        this.quality = quality;
        // Save settings
        Settings.updateSettings(id, volume, quality, muted);
    }

    // Starts video playback
    public void startVideo() {
        if (mediaPlayer != null) {
            mediaPlayer.play();
            videoStarted = true;
            paused = false;
        }
    }

    // Returns the paused state of the video
    public boolean getPaused() {
        return paused;
    }

    // Sets the paused state of the video
    public void setPaused(boolean paused) {
        if (!videoStarted) {
            this.paused = false;
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

        TextureManager manager = Minecraft.getInstance().getTextureManager();

        if (textureId != null) manager.release(textureId);
        if (previewTextureId != null) manager.release(previewTextureId);

        if (Minecraft.getInstance().screen instanceof Configuration displayConfScreen) {
            if (displayConfScreen.screen == this) displayConfScreen.onClose();
        }
    }

    @Nullable
    public DynamicTexture getPreviewTexture() {
        return previewTexture;
    }

    public boolean hasPreviewTexture() {
        return false;
    }

    public UUID getID() {
        return id;
    }

    public void mute(boolean status) {
        if (muted == status) return;
        muted = status;

        setVideoVolume(!status ? volume : 0);
        // Save settings
        Settings.updateSettings(id, volume, quality, muted);
    }

    public double getVolume() {
        return volume;
    }

    // Creates a new texture for the screen based on its dimensions and quality
    public void createTexture() {
        int qualityInt = Integer.parseInt(this.quality.replace("p", ""));
        textureWidth = (int) (width / (double) height * qualityInt);
        textureHeight = qualityInt;

        //textureId = RenderUtil2D.createEmptyTexture(textureWidth, textureHeight);
        if (texture != null) {
            texture.close();
            if (textureId != null) Minecraft.getInstance()
                    .getTextureManager()
                    .release(textureId);
        }
        texture = new DynamicTexture(UUID.randomUUID().toString(), textureWidth, textureHeight, true);
        textureId = Identifier.fromNamespaceAndPath(Initializer.MOD_ID, "screen-main-texture-" + id + "-" + UUID.randomUUID());

        Minecraft.getInstance().getTextureManager().register(textureId, texture);
        renderType = createRenderType(textureId);
    }

    public void sendSync() {
        if (mediaPlayer != null) {
            Initializer.sendPacket(new Sync(id, isSync, paused, mediaPlayer.getCurrentTime(), mediaPlayer.getDuration()));
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
        }).start();
    }

    public void tick(BlockPos pos) {
        if (mediaPlayer != null) mediaPlayer.tick(pos, Initializer.config.defaultDistance);
    }

    public void afterSeek() {
        if (owner && isSync) sendSync();
    }
}

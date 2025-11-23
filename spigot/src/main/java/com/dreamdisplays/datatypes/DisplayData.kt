package com.dreamdisplays.datatypes;

import com.dreamdisplays.DreamDisplaysPlugin;
import com.dreamdisplays.utils.Utils;
import com.dreamdisplays.utils.net.PacketUtils;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

import java.util.List;
import java.util.UUID;

public class DisplayData {
    private final UUID id;
    private final UUID ownerId;
    private final Location pos1;
    private final Location pos2;
    private final int width;
    private final int height;
    private String url = "";
    private final BlockFace facing;
    private Long duration = null;
    private boolean isSync = false;
    private String lang = "";

    public final BoundingBox box;

    // Cached bounds
    private final int minX, maxX;
    private final int minY, maxY;
    private final int minZ, maxZ;

    public DisplayData(
            UUID id,
            UUID ownerId,
            Location pos1,
            Location pos2,
            int width,
            int height,
            BlockFace facing
    ) {
        this.id = id;
        this.ownerId = ownerId;
        this.pos1 = pos1;
        this.pos2 = pos2;
        this.width = width;
        this.height = height;
        this.facing = facing;

        // Initialize bounds
        this.minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        this.minY = Math.min(pos1.getBlockY(), pos2.getBlockY());
        this.minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        this.maxX = Math.max(pos1.getBlockX(), pos2.getBlockX()) + 1;
        this.maxY = Math.max(pos1.getBlockY(), pos2.getBlockY()) + 1;
        this.maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ()) + 1;

        this.box = new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public int getWidth() {
        return width;
    }

    public Long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public int getHeight() {
        return height;
    }

    public UUID getId() {
        return id;
    }

    public boolean isSync() {
        return isSync;
    }

    public void setSync(boolean sync) {
        isSync = sync;
    }

    public Location getPos1() {
        return pos1;
    }

    public Location getPos2() {
        return pos2;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public BlockFace getFacing() {
        return facing;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    // Checks if a location is within render distance of the display
    public boolean isInRange(Location loc) {
        double maxRenderDistance = DreamDisplaysPlugin.config.settings.maxRenderDistance;
        double maxDistSq = maxRenderDistance * maxRenderDistance;

        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();

        int cx = x < minX ? minX : Math.min(x, maxX);
        int cy = y < minY ? minY : Math.min(y, maxY);
        int cz = z < minZ ? minZ : Math.min(z, maxZ);

        int dx = x - cx;
        int dy = y - cy;
        int dz = z - cz;

        return (dx * dx + dy * dy + dz * dz) <= maxDistSq;
    }

    // Sends an update packet to a list of players
    public void sendUpdatePacket(List<Player> players) {
        PacketUtils.sendDisplayInfoPacket(players, id, ownerId, box.getMin(), width, height, url, lang, facing, isSync);
    }

    public List<Player> getReceivers() {
        return pos1.getWorld().getPlayers()
                .stream()
                .filter(player ->
                        Utils.getDistanceToScreen(
                                player.getLocation(),
                                this
                        ) < DreamDisplaysPlugin.config.settings.maxRenderDistance
                )
                .toList();
    }

    public void setLang(String lang) {
        this.lang = lang;
    }

    public String getLang() {
        return lang;
    }
}

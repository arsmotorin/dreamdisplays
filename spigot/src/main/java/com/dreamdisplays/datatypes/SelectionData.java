package com.dreamdisplays.datatypes;

import com.dreamdisplays.DreamDisplaysPlugin;
import com.dreamdisplays.utils.ParticleUtil;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

import java.util.UUID;

public class SelectionData {
    private Location pos1;
    private Location pos2;
    private BlockFace face;
    private final UUID playerId;
    private boolean isReady;

    public SelectionData(Player player) {
        this.playerId = player.getUniqueId();
    }

    public void setPos1(Location location) {
        this.pos1 = location;
    }

    public void setPos2(Location location) {
        this.pos2 = location;
    }

    public void setFace(BlockFace blockFace) {
        this.face = blockFace;
    }

    public Location getPos1() {
        return pos1;
    }

    public Location getPos2() {
        return pos2;
    }

    public BlockFace getFace() {
        return this.face;
    }

    public boolean isReady() {
        return isReady;
    }

    public void setReady(boolean ready) {
        isReady = ready;
    }

    public void drawBox() {
        if (pos1 == null || pos2 == null) return;

        Player player = Bukkit.getPlayer(playerId);
        if (player == null) return;

        ParticleUtil.drawRectangleOnFace(player, pos1, pos2, face, DreamDisplaysPlugin.config.settings.particlesPerBlock, Color.fromRGB(DreamDisplaysPlugin.config.settings.particlesColor));
    }

    public DisplayData generateDisplayData() {
        int deltaX = Math.abs(pos1.getBlockX() - pos2.getBlockX())+1;
        int deltaZ = Math.abs(pos1.getBlockZ() - pos2.getBlockZ())+1;

        int width = Math.max(deltaX, deltaZ);
        int height = Math.abs(pos1.getBlockY() - pos2.getBlockY()) + 1;

        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int minY = Math.min(pos1.getBlockY(), pos2.getBlockY());
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());

        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int maxY = Math.max(pos1.getBlockY(), pos2.getBlockY());
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());

        Location dPos1 = new Location(pos1.getWorld(), minX, minY, minZ);
        Location dPos2 = new Location(pos1.getWorld(), maxX, maxY, maxZ);

        return new DisplayData(UUID.randomUUID(), playerId, dPos1, dPos2, width, height, face);
    }
}

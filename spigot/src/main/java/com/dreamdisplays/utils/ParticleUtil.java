package com.dreamdisplays.utils;

import me.inotsleep.utils.particle.Util;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

public class ParticleUtil {
    public static void drawLine(Player player, Location from, Location to, int particlesPerBlock, Color color) {
        double distance = from.distance(to);
        int particles = (int) (distance * particlesPerBlock);
        World world = from.getWorld();

        for (int i = 0; i <= particles; i++) {
            double t = i / (double) particles;
            double x = from.getX() + (to.getX() - from.getX()) * t;
            double y = from.getY() + (to.getY() - from.getY()) * t;
            double z = from.getZ() + (to.getZ() - from.getZ()) * t;

            Location particleLocation = new Location(world, x, y, z);
            Util.drawParticle(player, particleLocation.getX(), particleLocation.getY(), particleLocation.getZ(), 0, 0, 0, Particle.DUST, null, color.getRed(), color.getGreen(), color.getBlue());
        }
    }

    public static void drawRectangleOnFace(Player player, Location corner1, Location corner2, BlockFace face, int particlesPerBlock, Color color) {
        int minX = Math.min(corner1.getBlockX(), corner2.getBlockX());
        int minY = Math.min(corner1.getBlockY(), corner2.getBlockY());
        int minZ = Math.min(corner1.getBlockZ(), corner2.getBlockZ());
        int maxX = Math.max(corner1.getBlockX(), corner2.getBlockX()) + 1;
        int maxY = Math.max(corner1.getBlockY(), corner2.getBlockY()) + 1;
        int maxZ = Math.max(corner1.getBlockZ(), corner2.getBlockZ()) + 1;
        World world = corner1.getWorld();

        switch (face) {
            case UP:
                // Up face: fixed Y = maxY, XZ plane
                drawLine(player, new Location(world, minX, maxY, minZ), new Location(world, maxX, maxY, minZ), particlesPerBlock, color);
                drawLine(player, new Location(world, maxX, maxY, minZ), new Location(world, maxX, maxY, maxZ), particlesPerBlock, color);
                drawLine(player, new Location(world, maxX, maxY, maxZ), new Location(world, minX, maxY, maxZ), particlesPerBlock, color);
                drawLine(player, new Location(world, minX, maxY, maxZ), new Location(world, minX, maxY, minZ), particlesPerBlock, color);
                break;
            case DOWN:
                // Down face: fixed Y = minY, XZ plane
                drawLine(player, new Location(world, minX, minY, minZ), new Location(world, maxX, minY, minZ), particlesPerBlock, color);
                drawLine(player, new Location(world, maxX, minY, minZ), new Location(world, maxX, minY, maxZ), particlesPerBlock, color);
                drawLine(player, new Location(world, maxX, minY, maxZ), new Location(world, minX, minY, maxZ), particlesPerBlock, color);
                drawLine(player, new Location(world, minX, minY, maxZ), new Location(world, minX, minY, minZ), particlesPerBlock, color);
                break;
            case NORTH:
                // North face: fixed Z = minZ, XY plane
                drawLine(player, new Location(world, minX, minY, minZ), new Location(world, maxX, minY, minZ), particlesPerBlock, color);
                drawLine(player, new Location(world, maxX, minY, minZ), new Location(world, maxX, maxY, minZ), particlesPerBlock, color);
                drawLine(player, new Location(world, maxX, maxY, minZ), new Location(world, minX, maxY, minZ), particlesPerBlock, color);
                drawLine(player, new Location(world, minX, maxY, minZ), new Location(world, minX, minY, minZ), particlesPerBlock, color);
                break;
            case SOUTH:
                // South face: fixed Z = maxZ, XY plane
                drawLine(player, new Location(world, minX, minY, maxZ), new Location(world, maxX, minY, maxZ), particlesPerBlock, color);
                drawLine(player, new Location(world, maxX, minY, maxZ), new Location(world, maxX, maxY, maxZ), particlesPerBlock, color);
                drawLine(player, new Location(world, maxX, maxY, maxZ), new Location(world, minX, maxY, maxZ), particlesPerBlock, color);
                drawLine(player, new Location(world, minX, maxY, maxZ), new Location(world, minX, minY, maxZ), particlesPerBlock, color);
                break;
            case EAST:
                // East face: fixed X = maxX, YZ plane
                drawLine(player, new Location(world, maxX, minY, minZ), new Location(world, maxX, maxY, minZ), particlesPerBlock, color);
                drawLine(player, new Location(world, maxX, maxY, minZ), new Location(world, maxX, maxY, maxZ), particlesPerBlock, color);
                drawLine(player, new Location(world, maxX, maxY, maxZ), new Location(world, maxX, minY, maxZ), particlesPerBlock, color);
                drawLine(player, new Location(world, maxX, minY, maxZ), new Location(world, maxX, minY, minZ), particlesPerBlock, color);
                break;
            case WEST:
                // West face: fixed X = minX, YZ plane
                drawLine(player, new Location(world, minX, minY, minZ), new Location(world, minX, maxY, minZ), particlesPerBlock, color);
                drawLine(player, new Location(world, minX, maxY, minZ), new Location(world, minX, maxY, maxZ), particlesPerBlock, color);
                drawLine(player, new Location(world, minX, maxY, maxZ), new Location(world, minX, minY, maxZ), particlesPerBlock, color);
                drawLine(player, new Location(world, minX, minY, maxZ), new Location(world, minX, minY, minZ), particlesPerBlock, color);
                break;
            default:
                throw new IllegalArgumentException("Unsupported BlockFace for a flat rectangle: " + face);
        }
    }
}

package com.dreamdisplays.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

// Utility class for ray-casting operations
public class RCUtil {

    public static BlockHitResult rCBlock(double maxDistance) {
        Minecraft client = Minecraft.getInstance();

        if (client.player == null || client.level == null)
            return null;

        Vec3 start = client.player.getEyePosition(1.0f);
        Vec3 direction = client.player.getViewVector(1.0f);
        Vec3 end = start.add(direction.scale(maxDistance));

        BlockHitResult hit = client.level.clip(new net.minecraft.world.level.ClipContext(
            start,
            end,
            net.minecraft.world.level.ClipContext.Block.OUTLINE,
            net.minecraft.world.level.ClipContext.Fluid.NONE,
            client.player
        ));

        if (hit.getType() == HitResult.Type.BLOCK) {
            return hit;
        }

        return null;
    }
}

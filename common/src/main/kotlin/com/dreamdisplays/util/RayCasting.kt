package com.dreamdisplays.util

import net.minecraft.client.Minecraft
import net.minecraft.client.Minecraft.getInstance
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.ClipContext.Block
import net.minecraft.world.level.ClipContext.Fluid
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.HitResult.Type
import net.minecraft.world.phys.HitResult.Type.BLOCK
import org.jspecify.annotations.NullMarked

/**
 * Utility class for ray casting operations.
 */
@NullMarked
object RayCasting {

    @JvmStatic
    fun rCBlock(maxDistance: Double): BlockHitResult? {
        val minecraft = getInstance()
        val player = minecraft.player

        if (player == null || minecraft.level == null) return null

        val start = player.getEyePosition(1.0f)
        val direction = player.getViewVector(1.0f)
        val end = start.add(direction.scale(maxDistance))

        val hitResult = minecraft.level!!.clip(
            ClipContext(
                start,
                end,
                Block.OUTLINE,
                Fluid.NONE,
                player
            )
        )

        if (hitResult.type == BLOCK) {
            return hitResult
        }

        return null
    }
}

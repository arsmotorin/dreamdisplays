package com.dreamdisplays.util

import net.minecraft.client.Minecraft
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import org.jspecify.annotations.NullMarked

/**
 * Utility class for ray casting operations.
 */
@NullMarked
object RayCasting {

    @JvmStatic
    fun rCBlock(maxDistance: Double): BlockHitResult? {
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player

        if (player == null || minecraft.level == null) return null

        val start = player.getEyePosition(1.0f)
        val direction = player.getViewVector(1.0f)
        val end = start.add(direction.scale(maxDistance))

        val hitResult = minecraft.level!!.clip(
            ClipContext(
                start,
                end,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                player
            )
        )

        if (hitResult.type == HitResult.Type.BLOCK) {
            return hitResult
        }

        return null
    }
}

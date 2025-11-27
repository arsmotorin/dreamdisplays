package com.dreamdisplays.util

import net.minecraft.client.Minecraft
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import org.jspecify.annotations.NullMarked

/**
 * Rey-casting utility functions.
 */
@NullMarked
object RayCasting {
    fun rCBlock(maxDistance: Double): BlockHitResult? {
        val client = Minecraft.getInstance()

        if (client.player == null || client.level == null) return null

        val start = client.player!!.getEyePosition(1.0f)
        val direction = client.player!!.getViewVector(1.0f)
        val end = start.add(direction.scale(maxDistance))

        val hit = client.level!!.clip(
            ClipContext(
                start,
                end,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                client.player!!
            )
        )

        if (hit.type == HitResult.Type.BLOCK) {
            return hit
        }

        return null
    }
}

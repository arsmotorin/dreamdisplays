package com.dreamdisplays.render

import com.dreamdisplays.screen.Manager
import com.dreamdisplays.screen.Screen
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.Tesselator
import net.minecraft.client.Camera
import net.minecraft.world.phys.Vec3
import org.jspecify.annotations.NullMarked

/**
 * Renders all screens in the world and checks if they are in view of the camera.
 */
@NullMarked
object World {
    // Renders all screens in the world relative to the camera position
    @JvmStatic
    fun render(matrices: PoseStack, camera: Camera) {
        val cameraPos = camera.position()
        for (screen in Manager.getScreens()) {
            if (screen.texture == null) screen.createTexture()

            matrices.pushPose()

            // Translate the matrix stack to the player's screen position
            val pos = screen.pos
            val screenCenter = Vec3.atLowerCornerOf(pos)
            val relativePos = screenCenter.subtract(cameraPos)
            matrices.translate(relativePos.x, relativePos.y, relativePos.z)

            // Move the matrix stack forward based on the screen's facing direction
            val tessellator = Tesselator.getInstance()

            renderScreenTexture(screen, matrices, tessellator)

            matrices.popPose()
        }
    }

    // Renders the texture of a single screen
    private fun renderScreenTexture(screen: Screen, matrices: PoseStack, tessellator: Tesselator) {
        matrices.pushPose()
        Render.moveForward(matrices, screen.facing, 0.008f)

        when (screen.facing) {
            "NORTH" -> {
                Render.moveHorizontal(matrices, "NORTH", -(screen.getWidth()))
                Render.moveForward(matrices, "NORTH", 1f)
            }

            "SOUTH" -> {
                Render.moveHorizontal(matrices, "SOUTH", 1f)
                Render.moveForward(matrices, "SOUTH", 1f)
            }

            "EAST" -> {
                Render.moveHorizontal(matrices, "EAST", -(screen.getWidth() - 1))
                Render.moveForward(matrices, "EAST", 2f)
            }
        }

        // Fix the rotation of the matrix stack based on the screen's facing direction
        Render.fixRotation(matrices, screen.facing)
        matrices.scale(screen.getWidth(), screen.getHeight(), 0f)

        // Render the screen texture or preview texture
        if (screen.isVideoStarted()) {
            screen.fitTexture()
            Render.renderGpuTexture(matrices, tessellator, screen.texture!!.getTextureView(), screen.renderType!!)
        } else if (screen.hasPreviewTexture()) {
            Render.renderGpuTexture(
                matrices,
                tessellator,
                screen.previewTexture!!.getTextureView(),
                screen.previewRenderType!!
            )
        } else {
            Render.renderBlack(matrices, tessellator, screen.renderType!!)
        }
        matrices.popPose()
    }
}

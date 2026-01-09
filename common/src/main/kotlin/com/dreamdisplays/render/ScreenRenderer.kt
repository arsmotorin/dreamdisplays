package com.dreamdisplays.render

import com.dreamdisplays.screen.DisplayScreen
import com.dreamdisplays.screen.managers.ScreenManager.getScreens
import com.mojang.blaze3d.vertex.DefaultVertexFormat.BLOCK
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS
import net.minecraft.client.Camera
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf
import org.jspecify.annotations.NullMarked

/**
 * Renders screens in the world relative to the camera position.
 */
@NullMarked
object ScreenRenderer {
    fun render(stack: PoseStack, camera: Camera) {
        val cameraPos = camera.position()
        for (screen in getScreens()) {
            if (screen.texture == null) screen.createTexture()

            stack.pushPose()

            val pos = screen.getPos()
            val screenCenter = Vec3.atLowerCornerOf(pos)
            val relativePos = screenCenter.subtract(cameraPos)
            stack.translate(relativePos.x, relativePos.y, relativePos.z)

            renderScreenTexture(screen, stack, Tesselator.getInstance())

            stack.popPose()
        }
    }

    private fun renderScreenTexture(
        screen: DisplayScreen,
        stack: PoseStack,
        tessellator: Tesselator,
    ) {
        stack.pushPose()

        applyTransformations(stack, screen.getFacing(), screen.getWidth(), screen.getHeight())

        val type = screen.renderType
        if (type != null) {
            val isVideo = screen.isVideoStarted() && screen.texture != null
            val color = if (isVideo) 255 else 0
            drawQuad(stack, tessellator, type, color)
        }

        stack.popPose()
    }

    private fun applyTransformations(stack: PoseStack, facing: String, width: Float, height: Float) {
        when (facing) {
            "NORTH" -> {
                stack.translate(width, 0f, -0.008f)
                stack.mulPose(Quaternionf().rotationY(Math.toRadians(180.0).toFloat()))
                stack.scale(width, height, 0f)
            }

            "SOUTH" -> {
                stack.translate(0f, 0f, 1.008f)
                stack.scale(width, height, 0f)
            }

            "EAST" -> {
                stack.translate(1.008f, 0f, width)
                stack.mulPose(Quaternionf().rotationY(Math.toRadians(90.0).toFloat()))
                stack.scale(width, height, 0f)
            }

            "WEST" -> {
                stack.translate(-0.008f, 0f, 0f)
                stack.mulPose(Quaternionf().rotationY(Math.toRadians(-90.0).toFloat()))
                stack.scale(width, height, 0f)
            }

            "UP" -> {
                stack.translate(0f, 1f + 0.008f, height)
                stack.mulPose(Quaternionf().rotationX(Math.toRadians(-90.0).toFloat()))
                stack.scale(width, height, 1f)
            }

            "DOWN" -> {
                stack.translate(0f, -0.008f, 0f)
                stack.mulPose(Quaternionf().rotationX(Math.toRadians(90.0).toFloat()))
                stack.scale(width, height, 1f)
            }

            else -> {
                stack.translate(-1f, 0f, 0.008f)
                stack.scale(width, height, 0f)
            }
        }
    }

    private fun drawQuad(stack: PoseStack, tessellator: Tesselator, type: RenderType, colorVal: Int) {
        val pose = stack.last().pose()
        val builder = tessellator.begin(
            QUADS,
            BLOCK
        )

        builder
            .addVertex(pose, 0f, 0f, 0f)
            .setColor(colorVal, colorVal, colorVal, 255)
            .setUv(0f, 1f)
            .setLight(0xF000F0)
            .setNormal(0f, 0f, 1f)

        builder
            .addVertex(pose, 1f, 0f, 0f)
            .setColor(colorVal, colorVal, colorVal, 255)
            .setUv(1f, 1f)
            .setLight(0xF000F0)
            .setNormal(0f, 0f, 1f)

        builder
            .addVertex(pose, 1f, 1f, 0f)
            .setColor(colorVal, colorVal, colorVal, 255)
            .setUv(1f, 0f)
            .setLight(0xF000F0)
            .setNormal(0f, 0f, 1f)

        builder
            .addVertex(pose, 0f, 1f, 0f)
            .setColor(colorVal, colorVal, colorVal, 255)
            .setUv(0f, 0f)
            .setLight(0xF000F0)
            .setNormal(0f, 0f, 1f)

        val built = builder.buildOrThrow()
        type.draw(built)
    }
}

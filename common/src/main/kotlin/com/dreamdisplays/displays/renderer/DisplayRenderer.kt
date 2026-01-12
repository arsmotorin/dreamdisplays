package com.dreamdisplays.displays.renderer

import com.dreamdisplays.displays.DisplayScreen
import com.dreamdisplays.displays.managers.DisplayManager
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.Camera
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf
import org.jspecify.annotations.NullMarked

/**
 * Renders screens in the world relative to the camera position.
 */
@NullMarked
object DisplayRenderer {
    fun render(stack: PoseStack, camera: Camera) {
        val cameraPos = camera.position()
        for (display in DisplayManager.getDisplays()) {
            if (display.texture == null) display.createTexture()

            stack.pushPose()

            val pos = display.getPos()
            val displayCenter = Vec3.atLowerCornerOf(pos)
            val relativePos = displayCenter.subtract(cameraPos)
            stack.translate(relativePos.x, relativePos.y, relativePos.z)

            renderDisplayTexture(display, stack, Tesselator.getInstance())

            stack.popPose()
        }
    }

    private fun renderDisplayTexture(
        display: DisplayScreen,
        stack: PoseStack,
        tessellator: Tesselator,
    ) {
        stack.pushPose()

        applyTransformations(stack, display.getFacing(), display.getWidth(), display.getHeight())

        val type = display.renderType
        if (type != null) {
            val isVideo = display.isVideoStarted() && display.texture != null
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
            VertexFormat.Mode.QUADS,
            DefaultVertexFormat.BLOCK
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
package com.dreamdisplays.render

import com.mojang.blaze3d.textures.GpuTextureView
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.renderer.rendertype.RenderType
import org.joml.Quaternionf
import org.jspecify.annotations.NullMarked

/**
 * Main rendering base for rendering screens in the world.
 */
@NullMarked
object Render {
    // Prevent rotation issues
    fun fixRotation(stack: PoseStack, facing: String) {
        val rotation: Quaternionf

        when (facing) {
            "NORTH" -> {
                rotation = Quaternionf().rotationY(Math.toRadians(180.0).toFloat())
                stack.translate(0f, 0f, 1f)
            }

            "WEST" -> {
                rotation = Quaternionf().rotationY(Math.toRadians(-90.0).toFloat())
                stack.translate(0f, 0f, 0f)
            }

            "EAST" -> {
                rotation = Quaternionf().rotationY(Math.toRadians(90.0).toFloat())
                stack.translate(-1f, 0f, 1f)
            }

            else -> {
                rotation = Quaternionf()
                stack.translate(-1f, 0f, 0f)
            }
        }
        stack.mulPose(rotation)
    }

    // Moves the matrix stack forward based on the facing direction
    fun moveForward(stack: PoseStack, facing: String, amount: Float) {
        when (facing) {
            "NORTH" -> stack.translate(0f, 0f, -amount)
            "WEST" -> stack.translate(-amount, 0f, 0f)
            "EAST" -> stack.translate(amount, 0f, 0f)
            else -> stack.translate(0f, 0f, amount)
        }
    }

    // Moves the matrix stack horizontally based on the facing direction
    fun moveHorizontal(stack: PoseStack, facing: String, amount: Float) {
        when (facing) {
            "NORTH" -> stack.translate(-amount, 0f, 0f)
            "WEST" -> stack.translate(0f, 0f, amount)
            "EAST" -> stack.translate(0f, 0f, -amount)
            else -> stack.translate(amount, 0f, 0f)
        }
    }

    // Renders a GPU texture onto a quad using the provided matrix stack and tessellator
    fun renderGpuTexture(matrices: PoseStack, tess: Tesselator, @Suppress("UNUSED_PARAMETER") gpuView: GpuTextureView, type: RenderType) {
        val mat = matrices.last().pose()

        val buf = tess.begin(
            VertexFormat.Mode.QUADS,
            DefaultVertexFormat.BLOCK
        )

        buf
            .addVertex(mat, 0f, 0f, 0f)
            .setColor(255, 255, 255, 255)
            .setUv(0f, 1f)
            .setLight(0xF000F0)
            .setNormal(0f, 0f, 1f)

        buf
            .addVertex(mat, 1f, 0f, 0f)
            .setColor(255, 255, 255, 255)
            .setUv(1f, 1f)
            .setLight(0xF000F0)
            .setNormal(0f, 0f, 1f)

        buf
            .addVertex(mat, 1f, 1f, 0f)
            .setColor(255, 255, 255, 255)
            .setUv(1f, 0f)
            .setLight(0xF000F0)
            .setNormal(0f, 0f, 1f)

        buf
            .addVertex(mat, 0f, 1f, 0f)
            .setColor(255, 255, 255, 255)
            .setUv(0f, 0f)
            .setLight(0xF000F0)
            .setNormal(0f, 0f, 1f)

        val built = buf.buildOrThrow()
        type.draw(built)
    }

    // Renders a solid color square with the specified RGB values
    fun renderColor(matrices: PoseStack, tess: Tesselator, type: RenderType, r: Int, g: Int, b: Int) {
        val mat = matrices.last().pose()

        val buf = tess.begin(
            VertexFormat.Mode.QUADS,
            DefaultVertexFormat.BLOCK
        )

        buf
            .addVertex(mat, 0f, 0f, 0f)
            .setColor(r, g, b, 255)
            .setUv(0f, 1f)
            .setLight(0xF000F0)
            .setNormal(0f, 0f, 1f)

        buf
            .addVertex(mat, 1f, 0f, 0f)
            .setColor(r, g, b, 255)
            .setUv(1f, 1f)
            .setLight(0xF000F0)
            .setNormal(0f, 0f, 1f)

        buf
            .addVertex(mat, 1f, 1f, 0f)
            .setColor(r, g, b, 255)
            .setUv(1f, 0f)
            .setLight(0xF000F0)
            .setNormal(0f, 0f, 1f)

        buf
            .addVertex(mat, 0f, 1f, 0f)
            .setColor(r, g, b, 255)
            .setUv(0f, 0f)
            .setLight(0xF000F0)
            .setNormal(0f, 0f, 1f)

        val built = buf.buildOrThrow()
        type.draw(built)
    }

    // Renders a black square
    fun renderBlack(stack: PoseStack, tess: Tesselator, type: RenderType) {
        renderColor(stack, tess, type, 0, 0, 0)
    }
}

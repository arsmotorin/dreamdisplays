package com.dreamdisplays.render

import com.dreamdisplays.display.DisplayManager
import com.dreamdisplays.display.DisplayScreen
import com.mojang.blaze3d.vertex.*
import net.minecraft.client.Camera
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf
import kotlin.math.abs
import kotlin.math.sin

/** Renders screens in the world. */
object ScreenRenderer {
    fun render(stack: PoseStack, camera: Camera) {
        val cameraPos = camera.position()
        for (displayScreen in DisplayManager.getScreens()) {
            if (displayScreen.texture == null) displayScreen.createTexture()

            stack.pushPose()

            val pos = displayScreen.pos
            val screenCenter = Vec3.atLowerCornerOf(pos)
            val relativePos = screenCenter.subtract(cameraPos)
            stack.translate(relativePos.x, relativePos.y, relativePos.z)

            val tessellator = Tesselator.getInstance()
            renderScreenTexture(displayScreen, stack, tessellator)

            stack.popPose()
        }
    }

    private fun renderScreenTexture(displayScreen: DisplayScreen, stack: PoseStack, tessellator: Tesselator) {
        stack.pushPose()
        moveForward(stack, displayScreen.facing, 0.008f)

        when (displayScreen.facing) {
            "NORTH" -> {
                moveHorizontal(stack, "NORTH", -displayScreen.width.toFloat())
                moveForward(stack, "NORTH", 1f)
            }

            "SOUTH" -> {
                moveHorizontal(stack, "SOUTH", 1f)
                moveForward(stack, "SOUTH", 1f)
            }

            "EAST" -> {
                moveHorizontal(stack, "EAST", -(displayScreen.width - 1).toFloat())
                moveForward(stack, "EAST", 2f)
            }
        }

        fixRotation(stack, displayScreen.facing)
        stack.scale(displayScreen.width.toFloat(), displayScreen.height.toFloat(), 0f)

        if (displayScreen.isVideoStarted && displayScreen.texture != null && displayScreen.renderType != null) {
            renderGpuTexture(stack, tessellator, displayScreen.renderType!!, displayScreen.brightness)
        } else if (displayScreen.renderType != null) {
            if (displayScreen.errored) {
                renderColor(stack, tessellator, displayScreen.renderType!!, 35, 5, 5)
            } else {
                val pulse = abs(sin(System.nanoTime() / 1_500_000_000.0 * Math.PI)).toFloat()
                val v = (10 + pulse * 20).toInt()
                renderColor(stack, tessellator, displayScreen.renderType!!, v, v, v)
            }
        }
        stack.popPose()
    }

    private fun fixRotation(stack: PoseStack, facing: String) {
        val rotation: Quaternionf = when (facing) {
            "NORTH" -> Quaternionf().rotationY(Math.toRadians(180.0).toFloat()).also {
                stack.translate(0f, 0f, 1f)
            }

            "WEST" -> Quaternionf().rotationY(Math.toRadians(-90.0).toFloat()).also {
                stack.translate(0f, 0f, 0f)
            }

            "EAST" -> Quaternionf().rotationY(Math.toRadians(90.0).toFloat()).also {
                stack.translate(-1f, 0f, 1f)
            }

            else -> Quaternionf().also { stack.translate(-1f, 0f, 0f) }
        }
        stack.mulPose(rotation)
    }

    private fun moveForward(stack: PoseStack, facing: String, amount: Float) {
        when (facing) {
            "NORTH" -> stack.translate(0f, 0f, -amount)
            "WEST" -> stack.translate(-amount, 0f, 0f)
            "EAST" -> stack.translate(amount, 0f, 0f)
            else -> stack.translate(0f, 0f, amount)
        }
    }

    private fun moveHorizontal(stack: PoseStack, facing: String, amount: Float) {
        when (facing) {
            "NORTH" -> stack.translate(-amount, 0f, 0f)
            "WEST" -> stack.translate(0f, 0f, amount)
            "EAST" -> stack.translate(0f, 0f, -amount)
            else -> stack.translate(amount, 0f, 0f)
        }
    }

    private fun renderGpuTexture(stack: PoseStack, tesselator: Tesselator, type: RenderType, brightness: Float) {
        val pose = stack.last().pose()
        val c = (brightness.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        val builder: BufferBuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK)

        builder.addVertex(pose, 0f, 0f, 0f).setColor(c, c, c, 255).setUv(0f, 1f).setLight(0xF000F0).setNormal(0f, 0f, 1f)
        builder.addVertex(pose, 1f, 0f, 0f).setColor(c, c, c, 255).setUv(1f, 1f).setLight(0xF000F0).setNormal(0f, 0f, 1f)
        builder.addVertex(pose, 1f, 1f, 0f).setColor(c, c, c, 255).setUv(1f, 0f).setLight(0xF000F0).setNormal(0f, 0f, 1f)
        builder.addVertex(pose, 0f, 1f, 0f).setColor(c, c, c, 255).setUv(0f, 0f).setLight(0xF000F0).setNormal(0f, 0f, 1f)

        type.draw(builder.buildOrThrow())
    }

    private fun renderColor(stack: PoseStack, tesselator: Tesselator, type: RenderType, r: Int, g: Int, b: Int) {
        val pose = stack.last().pose()
        val builder: BufferBuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK)

        builder.addVertex(pose, 0f, 0f, 0f).setColor(r, g, b, 255).setUv(0f, 1f).setLight(0xF000F0)
            .setNormal(0f, 0f, 1f)
        builder.addVertex(pose, 1f, 0f, 0f).setColor(r, g, b, 255).setUv(1f, 1f).setLight(0xF000F0)
            .setNormal(0f, 0f, 1f)
        builder.addVertex(pose, 1f, 1f, 0f).setColor(r, g, b, 255).setUv(1f, 0f).setLight(0xF000F0)
            .setNormal(0f, 0f, 1f)
        builder.addVertex(pose, 0f, 1f, 0f).setColor(r, g, b, 255).setUv(0f, 0f).setLight(0xF000F0)
            .setNormal(0f, 0f, 1f)

        type.draw(builder.buildOrThrow())
    }
}

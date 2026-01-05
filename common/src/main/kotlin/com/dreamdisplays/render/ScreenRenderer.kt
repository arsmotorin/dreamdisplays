package com.dreamdisplays.render

import com.dreamdisplays.screen.Manager
import com.dreamdisplays.screen.Screen
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
 * Renders screens in the world.
 */
@NullMarked
object ScreenRenderer {

    // Renders all screens in the world relative to the camera position
    @JvmStatic
    fun render(stack: PoseStack, camera: Camera) {
        val cameraPos = camera.position()
        for (screen in Manager.getScreens()) {
            if (screen.texture == null) screen.createTexture()

            stack.pushPose()

            // Translate the matrix stack to the player's screen position
            val pos = screen.getPos()
            val screenCenter = Vec3.atLowerCornerOf(pos)
            val relativePos = screenCenter.subtract(cameraPos)
            stack.translate(relativePos.x, relativePos.y, relativePos.z)

            // Move the matrix stack forward based on the screen's facing direction
            val tessellator = Tesselator.getInstance()

            renderScreenTexture(screen, stack, tessellator)

            stack.popPose()
        }
    }

    // Renders the texture of a single screen
    private fun renderScreenTexture(
        screen: Screen,
        stack: PoseStack,
        tessellator: Tesselator
    ) {
        stack.pushPose()
        moveForward(stack, screen.getFacing(), 0.008f)

        when (screen.getFacing()) {
            "NORTH" -> {
                moveHorizontal(stack, "NORTH", -(screen.getWidth()))
                moveForward(stack, "NORTH", 1f)
            }
            "SOUTH" -> {
                moveHorizontal(stack, "SOUTH", 1f)
                moveForward(stack, "SOUTH", 1f)
            }
            "EAST" -> {
                moveHorizontal(stack, "EAST", -(screen.getWidth() - 1))
                moveForward(stack, "EAST", 2f)
            }
        }

        // Fix the rotation of the matrix stack based on the screen's facing direction
        fixRotation(stack, screen.getFacing())
        stack.scale(screen.getWidth(), screen.getHeight(), 0f)

        // Render the screen texture or black square
        if (screen.isVideoStarted() &&
            screen.texture != null &&
            screen.renderType != null
        ) {
            renderGpuTexture(stack, tessellator, screen.renderType!!)
        } else if (screen.renderType != null) {
            renderColor(stack, tessellator, screen.renderType!!)
        }
        stack.popPose()
    }

    // Prevent rotation issues
    private fun fixRotation(stack: PoseStack, facing: String) {
        val rotation: Quaternionf

        when (facing) {
            "NORTH" -> {
                rotation = Quaternionf().rotationY(
                    Math.toRadians(180.0).toFloat()
                )
                stack.translate(0f, 0f, 1f)
            }
            "WEST" -> {
                rotation = Quaternionf().rotationY(
                    Math.toRadians(-90.0).toFloat()
                )
                stack.translate(0f, 0f, 0f)
            }
            "EAST" -> {
                rotation = Quaternionf().rotationY(
                    Math.toRadians(90.0).toFloat()
                )
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
    private fun moveForward(
        stack: PoseStack,
        facing: String,
        amount: Float
    ) {
        when (facing) {
            "NORTH" -> stack.translate(0f, 0f, -amount)
            "WEST" -> stack.translate(-amount, 0f, 0f)
            "EAST" -> stack.translate(amount, 0f, 0f)
            else -> stack.translate(0f, 0f, amount)
        }
    }

    // Moves the matrix stack horizontally based on the facing direction
    private fun moveHorizontal(
        stack: PoseStack,
        facing: String,
        amount: Float
    ) {
        when (facing) {
            "NORTH" -> stack.translate(-amount, 0f, 0f)
            "WEST" -> stack.translate(0f, 0f, amount)
            "EAST" -> stack.translate(0f, 0f, -amount)
            else -> stack.translate(amount, 0f, 0f)
        }
    }

    // Renders a GPU texture onto a quad using the provided matrix stack and tessellator
    private fun renderGpuTexture(
        stack: PoseStack,
        tesselator: Tesselator,
        type: RenderType
    ) {
        val pose = stack.last().pose()

        val builder = tesselator.begin(
            VertexFormat.Mode.QUADS,
            DefaultVertexFormat.BLOCK
        )

        builder
            .addVertex(pose, 0f, 0f, 0f)
            .setColor(255, 255, 255, 255)
            .setUv(0f, 1f)
            .setLight(0xF000F0)
            .setNormal(0f, 0f, 1f)

        builder
            .addVertex(pose, 1f, 0f, 0f)
            .setColor(255, 255, 255, 255)
            .setUv(1f, 1f)
            .setLight(0xF000F0)
            .setNormal(0f, 0f, 1f)

        builder
            .addVertex(pose, 1f, 1f, 0f)
            .setColor(255, 255, 255, 255)
            .setUv(1f, 0f)
            .setLight(0xF000F0)
            .setNormal(0f, 0f, 1f)

        builder
            .addVertex(pose, 0f, 1f, 0f)
            .setColor(255, 255, 255, 255)
            .setUv(0f, 0f)
            .setLight(0xF000F0)
            .setNormal(0f, 0f, 1f)

        val built = builder.buildOrThrow()
        type.draw(built)
    }

    // Renders a solid color square with the specified RGB values
    private fun renderColor(
        stack: PoseStack,
        tesselator: Tesselator,
        type: RenderType
    ) {
        val pose = stack.last().pose()

        val builder = tesselator.begin(
            VertexFormat.Mode.QUADS,
            DefaultVertexFormat.BLOCK
        )

        builder
            .addVertex(pose, 0f, 0f, 0f)
            .setColor(0, 0, 0, 255)
            .setUv(0f, 1f)
            .setLight(0xF000F0)
            .setNormal(0f, 0f, 1f)

        builder
            .addVertex(pose, 1f, 0f, 0f)
            .setColor(0, 0, 0, 255)
            .setUv(1f, 1f)
            .setLight(0xF000F0)
            .setNormal(0f, 0f, 1f)

        builder
            .addVertex(pose, 1f, 1f, 0f)
            .setColor(0, 0, 0, 255)
            .setUv(1f, 0f)
            .setLight(0xF000F0)
            .setNormal(0f, 0f, 1f)

        builder
            .addVertex(pose, 0f, 1f, 0f)
            .setColor(0, 0, 0, 255)
            .setUv(0f, 0f)
            .setLight(0xF000F0)
            .setNormal(0f, 0f, 1f)

        val built = builder.buildOrThrow()
        type.draw(built)
    }
}

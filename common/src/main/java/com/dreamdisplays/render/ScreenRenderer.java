package com.dreamdisplays.render;

import com.dreamdisplays.screen.Manager;
import com.dreamdisplays.screen.Screen;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.jspecify.annotations.NullMarked;

/**
 * Renders screens in the world.
 */
@NullMarked
public class ScreenRenderer {

    // Renders all screens in the world relative to the camera position
    public static void render(PoseStack stack, Camera camera) {
        Vec3 cameraPos = camera.position();
        for (Screen screen : Manager.getScreens()) {
            if (screen.texture == null) screen.createTexture();

            stack.pushPose();

            // Translate the matrix stack to the player's screen position
            BlockPos pos = screen.getPos();
            Vec3 screenCenter = Vec3.atLowerCornerOf(pos);
            Vec3 relativePos = screenCenter.subtract(cameraPos);
            stack.translate(relativePos.x, relativePos.y, relativePos.z);

            // Move the matrix stack forward based on the screen's facing direction
            Tesselator tessellator = Tesselator.getInstance();

            renderScreenTexture(screen, stack, tessellator);

            stack.popPose();
        }
    }

    // Renders the texture of a single screen
    private static void renderScreenTexture(
            Screen screen,
            PoseStack stack,
            Tesselator tessellator
    ) {
        stack.pushPose();

        String facing = screen.getFacing();
        float width = screen.getWidth();
        float height = screen.getHeight();

        switch (facing) {
            case "NORTH":
                moveForward(stack, "NORTH", 0.008f);
                moveHorizontal(stack, "NORTH", -width);
                moveForward(stack, "NORTH", 1);
                fixRotation(stack, facing);
                stack.scale(width, height, 0);
                break;
            case "SOUTH":
                moveForward(stack, "SOUTH", 0.008f);
                moveHorizontal(stack, "SOUTH", 1);
                moveForward(stack, "SOUTH", 1);
                fixRotation(stack, facing);
                stack.scale(width, height, 0);
                break;
            case "EAST":
                moveForward(stack, "EAST", 0.008f);
                moveHorizontal(stack, "EAST", -(width - 1));
                moveForward(stack, "EAST", 2);
                fixRotation(stack, facing);
                stack.scale(width, height, 0);
                break;
            case "WEST":
                moveForward(stack, "WEST", 0.008f);
                fixRotation(stack, facing);
                stack.scale(width, height, 0);
                break;
            case "UP":
                stack.translate(0, 1 + 0.008f, height);
                stack.mulPose(new Quaternionf().rotationX((float) Math.toRadians(-90.0)));
                stack.scale(width, height, 1);
                break;
            case "DOWN":
                stack.translate(0, -0.008f, 0);
                stack.mulPose(new Quaternionf().rotationX((float) Math.toRadians(90.0)));
                stack.scale(width, height, 1);
                break;
            default:
                moveForward(stack, facing, 0.008f);
                fixRotation(stack, facing);
                stack.scale(width, height, 0);
                break;
        }

        // Render the screen texture or black square
        if (
                screen.isVideoStarted() &&
                        screen.texture != null &&
                        screen.renderType != null
        ) {
            renderGpuTexture(stack, tessellator, screen.renderType);
        } else if (screen.renderType != null) {
            renderColor(stack, tessellator, screen.renderType);
        }
        stack.popPose();
    }

    // Prevent rotation issues
    private static void fixRotation(PoseStack stack, String facing) {
        final Quaternionf rotation;

        switch (facing) {
            case "NORTH":
                rotation = new Quaternionf().rotationY(
                        (float) Math.toRadians(180)
                );
                stack.translate(0, 0, 1);
                break;
            case "WEST":
                rotation = new Quaternionf().rotationY(
                        (float) Math.toRadians(-90.0)
                );
                stack.translate(0, 0, 0);
                break;
            case "EAST":
                rotation = new Quaternionf().rotationY(
                        (float) Math.toRadians(90.0)
                );
                stack.translate(-1, 0, 1);
                break;
            case "UP":
                rotation = new Quaternionf().rotationX(
                        (float) Math.toRadians(-90.0)
                );
                break;
            case "DOWN":
                rotation = new Quaternionf().rotationX(
                        (float) Math.toRadians(90.0)
                );
                stack.translate(0, 1, 0);
                break;
            default:
                rotation = new Quaternionf();
                stack.translate(-1, 0, 0);
                break;
        }
        stack.mulPose(rotation);
    }

    // Moves the matrix stack forward based on the facing direction
    private static void moveForward(
            PoseStack stack,
            String facing,
            float amount
    ) {
        switch (facing) {
            case "NORTH":
                stack.translate(0, 0, -amount);
                break;
            case "WEST":
                stack.translate(-amount, 0, 0);
                break;
            case "EAST":
                stack.translate(amount, 0, 0);
                break;
            case "UP":
                stack.translate(0, amount, 0);
                break;
            case "DOWN":
                stack.translate(0, -amount, 0);
                break;
            default:
                stack.translate(0, 0, amount);
                break;
        }
    }

    // Moves the matrix stack horizontally based on the facing direction
    private static void moveHorizontal(
            PoseStack stack,
            String facing,
            float amount
    ) {
        switch (facing) {
            case "NORTH":
                stack.translate(-amount, 0, 0);
                break;
            case "WEST":
                stack.translate(0, 0, amount);
                break;
            case "EAST":
                stack.translate(0, 0, -amount);
                break;
            case "UP":
            case "DOWN":
                stack.translate(amount, 0, 0);
                break;
            default:
                stack.translate(amount, 0, 0);
                break;
        }
    }

    // Renders a GPU texture onto a quad using the provided matrix stack and tessellator
    private static void renderGpuTexture(
            PoseStack stack,
            Tesselator tesselator,
            RenderType type
    ) {
        Matrix4f pose = stack.last().pose();

        BufferBuilder builder = tesselator.begin(
                VertexFormat.Mode.QUADS,
                DefaultVertexFormat.BLOCK
        );

        builder
                .addVertex(pose, 0f, 0f, 0f)
                .setColor(255, 255, 255, 255)
                .setUv(0f, 1f)
                .setLight(0xF000F0)
                .setNormal(0f, 0f, 1f);

        builder
                .addVertex(pose, 1f, 0f, 0f)
                .setColor(255, 255, 255, 255)
                .setUv(1f, 1f)
                .setLight(0xF000F0)
                .setNormal(0f, 0f, 1f);

        builder
                .addVertex(pose, 1f, 1f, 0f)
                .setColor(255, 255, 255, 255)
                .setUv(1f, 0f)
                .setLight(0xF000F0)
                .setNormal(0f, 0f, 1f);

        builder
                .addVertex(pose, 0f, 1f, 0f)
                .setColor(255, 255, 255, 255)
                .setUv(0f, 0f)
                .setLight(0xF000F0)
                .setNormal(0f, 0f, 1f);

        MeshData built = builder.buildOrThrow();
        type.draw(built);
    }

    // Renders a solid color square with the specified RGB values
    private static void renderColor(
            PoseStack stack,
            Tesselator tesselator,
            RenderType type
    ) {
        Matrix4f pose = stack.last().pose();

        BufferBuilder builder = tesselator.begin(
                VertexFormat.Mode.QUADS,
                DefaultVertexFormat.BLOCK
        );

        builder
                .addVertex(pose, 0f, 0f, 0f)
                .setColor(0, 0, 0, 255)
                .setUv(0f, 1f)
                .setLight(0xF000F0)
                .setNormal(0f, 0f, 1f);

        builder
                .addVertex(pose, 1f, 0f, 0f)
                .setColor(0, 0, 0, 255)
                .setUv(1f, 1f)
                .setLight(0xF000F0)
                .setNormal(0f, 0f, 1f);

        builder
                .addVertex(pose, 1f, 1f, 0f)
                .setColor(0, 0, 0, 255)
                .setUv(1f, 0f)
                .setLight(0xF000F0)
                .setNormal(0f, 0f, 1f);

        builder
                .addVertex(pose, 0f, 1f, 0f)
                .setColor(0, 0, 0, 255)
                .setUv(0f, 0f)
                .setLight(0xF000F0)
                .setNormal(0f, 0f, 1f);

        MeshData built = builder.buildOrThrow();
        type.draw(built);
    }
}

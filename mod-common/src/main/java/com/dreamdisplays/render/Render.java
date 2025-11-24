package com.dreamdisplays.render;

import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class Render {

    // Prevent rotation issues
    public static void fixRotation(PoseStack stack, String facing) {
        final Quaternionf rotation;

        switch (facing) {
            case "NORTH":
                rotation = new Quaternionf().rotationY((float) Math.toRadians(180));
                stack.translate(0, 0, 1);
                break;
            case "WEST":
                rotation = new Quaternionf().rotationY((float) Math.toRadians(-90.0));
                stack.translate(0, 0, 0);
                break;
            case "EAST":
                rotation = new Quaternionf().rotationY((float) Math.toRadians(90.0));
                stack.translate(-1, 0, 1);
                break;
            default:
                rotation = new Quaternionf();
                stack.translate(-1, 0, 0);
                break;
        }
        stack.mulPose(rotation);
    }

    // Moves the matrix stack forward based on the facing direction
    public static void moveForward(PoseStack stack, String facing, float amount) {
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
            default:
                stack.translate(0, 0, amount);
                break;
        }
    }

    // Moves the matrix stack horizontally based on the facing direction
    public static void moveHorizontal(PoseStack stack, String facing, float amount) {
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
            default:
                stack.translate(amount, 0, 0);
                break;
        }
    }

    // Renders a GPU texture onto a quad using the provided matrix stack and tessellator
    public static void renderGpuTexture(PoseStack matrices, Tesselator tess, GpuTextureView gpuView, RenderType type) {
        // RenderSystem.setShaderLights(0, gpuView); // FIXME: do we need this? refer to RenderUtils2D comment
        Matrix4f mat = matrices.last().pose();

        BufferBuilder buf = tess.begin(
                VertexFormat.Mode.QUADS,
                DefaultVertexFormat.BLOCK
        );

        buf
                .addVertex(mat, 0f, 0f, 0f)
                .setColor(255, 255, 255, 255)
                .setUv(0f, 1f)
                .setLight(0xF000F0)
                .setNormal(0f, 0f, 1f);

        buf
                .addVertex(mat, 1f, 0f, 0f)
                .setColor(255, 255, 255, 255)
                .setUv(1f, 1f)
                .setLight(0xF000F0)
                .setNormal(0f, 0f, 1f);

        buf
                .addVertex(mat, 1f, 1f, 0f)
                .setColor(255, 255, 255, 255)
                .setUv(1f, 0f)
                .setLight(0xF000F0)
                .setNormal(0f, 0f, 1f);

        buf
                .addVertex(mat, 0f, 1f, 0f)
                .setColor(255, 255, 255, 255)
                .setUv(0f, 0f)
                .setLight(0xF000F0)
                .setNormal(0f, 0f, 1f);

        MeshData built = buf.buildOrThrow();
        type.draw(built);
    }

    // Renders a solid color square with the specified RGB values
    public static void renderColor(PoseStack matrices, Tesselator tess, RenderType type, int r, int g, int b) {
        Matrix4f mat = matrices.last().pose();

        BufferBuilder buf = tess.begin(
                VertexFormat.Mode.QUADS,
                DefaultVertexFormat.BLOCK
        );

        buf
                .addVertex(mat, 0f, 0f, 0f)
                .setColor(r, g, b, 255)
                .setUv(0f, 1f)
                .setLight(0xF000F0)
                .setNormal(0f, 0f, 1f);

        buf
                .addVertex(mat, 1f, 0f, 0f)
                .setColor(r, g, b, 255)
                .setUv(1f, 1f)
                .setLight(0xF000F0)
                .setNormal(0f, 0f, 1f);

        buf
                .addVertex(mat, 1f, 1f, 0f)
                .setColor(r, g, b, 255)
                .setUv(1f, 0f)
                .setLight(0xF000F0)
                .setNormal(0f, 0f, 1f);

        buf
                .addVertex(mat, 0f, 1f, 0f)
                .setColor(r, g, b, 255)
                .setUv(0f, 0f)
                .setLight(0xF000F0)
                .setNormal(0f, 0f, 1f);

        MeshData built = buf.buildOrThrow();
        type.draw(built);
    }

    // Renders a black square
    public static void renderBlack(PoseStack stack, Tesselator tess, RenderType type) {
        renderColor(stack, tess, type, 0, 0, 0);
    }
}

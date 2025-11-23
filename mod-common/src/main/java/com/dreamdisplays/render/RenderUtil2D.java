package com.dreamdisplays.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.joml.Matrix3x2fStack;
import org.jspecify.annotations.NullMarked;

// Utility class for rendering 2D textured quads
@NullMarked
public class RenderUtil2D {
    public static void drawTexturedQuad(Matrix3x2fStack matrices, GpuTextureView gpuView, float x, float y, float width, float height, RenderType renderType) {
        //RenderSystem.setShaderLights(0, gpuView); // FIXME: make this work or perhaps we dont need this at all, theres no more shaderTexture only lights

        float x0 = matrices.m00() * x + matrices.m10() * y + matrices.m20();
        float y0 = matrices.m01() * x + matrices.m11() * y + matrices.m21();
        float x1 = matrices.m00() * (x + width) + matrices.m10() * (y + height) + matrices.m20();
        float y1 = matrices.m01() * (x + width) + matrices.m11() * (y + height) + matrices.m21();

        BufferBuilder buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS,
                DefaultVertexFormat.BLOCK
        );

        buffer
                .addVertex(x0, y1, 0.0F)
                .setColor(255, 255, 255, 255)
                .setLight(0xF000F0)
                .setNormal(0f, 0f, 1f)
                .setUv(0.0f, 1.0f);
        buffer
                .addVertex(x1, y1, 0.0F)
                .setColor(255, 255, 255, 255)
                .setLight(0xF000F0)
                .setNormal(0f, 0f, 1f)
                .setUv(1.0f, 1.0f);
        buffer
                .addVertex(x1, y0, 0.0F)
                .setColor(255, 255, 255, 255)
                .setLight(0xF000F0)
                .setNormal(0f, 0f, 1f)
                .setUv(1.0f, 0.0f);
        buffer
                .addVertex(x0, y0, 0.0F)
                .setColor(255, 255, 255, 255)
                .setLight(0xF000F0)
                .setNormal(0f, 0f, 1f)
                .setUv(0.0f, 0.0f);

        renderType.draw(buffer.buildOrThrow());
    }
}

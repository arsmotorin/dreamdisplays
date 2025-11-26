package com.dreamdisplays.render

import com.mojang.blaze3d.textures.GpuTextureView
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.renderer.rendertype.RenderType
import org.joml.Matrix3x2fStack
import org.jspecify.annotations.NullMarked

// Utility class for rendering 2D textured quads
@NullMarked
object Render2D {
    fun drawTexturedQuad(
        matrices: Matrix3x2fStack,
        @Suppress("UNUSED_PARAMETER") gpuView: GpuTextureView,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        renderType: RenderType
    ) {
        val x0 = matrices.m00() * x + matrices.m10() * y + matrices.m20()
        val y0 = matrices.m01() * x + matrices.m11() * y + matrices.m21()
        val x1 = matrices.m00() * (x + width) + matrices.m10() * (y + height) + matrices.m20()
        val y1 = matrices.m01() * (x + width) + matrices.m11() * (y + height) + matrices.m21()

        val buffer = Tesselator.getInstance().begin(
            VertexFormat.Mode.QUADS,
            DefaultVertexFormat.BLOCK
        )

        buffer
            .addVertex(x0, y1, 0.0f)
            .setColor(255, 255, 255, 255)
            .setLight(0xF000F0)
            .setNormal(0f, 0f, 1f)
            .setUv(0.0f, 1.0f)
        buffer
            .addVertex(x1, y1, 0.0f)
            .setColor(255, 255, 255, 255)
            .setLight(0xF000F0)
            .setNormal(0f, 0f, 1f)
            .setUv(1.0f, 1.0f)
        buffer
            .addVertex(x1, y0, 0.0f)
            .setColor(255, 255, 255, 255)
            .setLight(0xF000F0)
            .setNormal(0f, 0f, 1f)
            .setUv(1.0f, 0.0f)
        buffer
            .addVertex(x0, y0, 0.0f)
            .setColor(255, 255, 255, 255)
            .setLight(0xF000F0)
            .setNormal(0f, 0f, 1f)
            .setUv(0.0f, 0.0f)

        renderType.draw(buffer.buildOrThrow())
    }
}

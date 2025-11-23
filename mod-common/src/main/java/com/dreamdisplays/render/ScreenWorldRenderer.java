package com.dreamdisplays.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import net.minecraft.client.Camera;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import com.dreamdisplays.screen.Screen;
import com.dreamdisplays.screen.ScreenManager;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class ScreenWorldRenderer {

    // Renders all screens in the world relative to the camera position
    public static void render(PoseStack matrices, Camera camera) {
        Vec3 cameraPos = camera.position();
        for (Screen screen : ScreenManager.getScreens()) {
            if (screen.texture == null) screen.createTexture();

            matrices.pushPose();

            // Translate the matrix stack to the player's screen position
            BlockPos pos = screen.getPos();
            Vec3 screenCenter = Vec3.atLowerCornerOf(pos);
            Vec3 relativePos = screenCenter.subtract(cameraPos);
            matrices.translate(relativePos.x, relativePos.y, relativePos.z);

            // Move the matrix stack forward based on the screen's facing direction
            Tesselator tessellator = Tesselator.getInstance();

            renderScreenTexture(screen, matrices, tessellator);

            matrices.popPose();
        }
    }

    // Renders the texture of a single screen
    private static void renderScreenTexture(Screen screen, PoseStack matrices, Tesselator tessellator) {
        matrices.pushPose();
        RenderUtil.moveForward(matrices, screen.getFacing(), 0.008f);

        switch (screen.getFacing()) {
            case "NORTH":
                RenderUtil.moveHorizontal(matrices, "NORTH", -(screen.getWidth()));
                RenderUtil.moveForward(matrices, "NORTH", 1);
                break;
            case "SOUTH":
                RenderUtil.moveHorizontal(matrices, "SOUTH", 1);
                RenderUtil.moveForward(matrices, "SOUTH", 1);
                break;
            case "EAST":
                RenderUtil.moveHorizontal(matrices, "EAST", -(screen.getWidth() - 1));
                RenderUtil.moveForward(matrices, "EAST", 2);
                break;
        }

        // Fix the rotation of the matrix stack based on the screen's facing direction
        RenderUtil.fixRotation(matrices, screen.getFacing());
        matrices.scale(screen.getWidth(), screen.getHeight(), 0);

        // Render the screen texture or preview texture
        if (screen.isVideoStarted()) {
            screen.fitTexture();
            RenderUtil.renderGpuTexture(matrices, tessellator, screen.texture.getTextureView(), screen.renderType);
        } else if (screen.hasPreviewTexture()) {
            RenderUtil.renderGpuTexture(matrices, tessellator, screen.getPreviewTexture().getTextureView(), screen.previewRenderType);
        } else {
            RenderUtil.renderBlack(matrices, tessellator, screen.renderType);
        }
        matrices.popPose();
    }
}

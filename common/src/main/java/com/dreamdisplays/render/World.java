package com.dreamdisplays.render;

import com.dreamdisplays.screen.Manager;
import com.dreamdisplays.screen.Screen;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import net.minecraft.client.Camera;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class World {

    // Renders all screens in the world relative to the camera position
    public static void render(PoseStack matrices, Camera camera) {
        Vec3 cameraPos = camera.position();
        for (Screen screen : Manager.getScreens()) {
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
        Render.moveForward(matrices, screen.getFacing(), 0.008f);

        switch (screen.getFacing()) {
            case "NORTH":
                Render.moveHorizontal(matrices, "NORTH", -(screen.getWidth()));
                Render.moveForward(matrices, "NORTH", 1);
                break;
            case "SOUTH":
                Render.moveHorizontal(matrices, "SOUTH", 1);
                Render.moveForward(matrices, "SOUTH", 1);
                break;
            case "EAST":
                Render.moveHorizontal(matrices, "EAST", -(screen.getWidth() - 1));
                Render.moveForward(matrices, "EAST", 2);
                break;
        }

        // Fix the rotation of the matrix stack based on the screen's facing direction
        Render.fixRotation(matrices, screen.getFacing());
        matrices.scale(screen.getWidth(), screen.getHeight(), 0);

        // Render the screen texture or preview texture
        if (screen.isVideoStarted()) {
            screen.fitTexture();
            Render.renderGpuTexture(matrices, tessellator, screen.texture.getTextureView(), screen.renderType);
        } else if (screen.hasPreviewTexture()) {
            Render.renderGpuTexture(matrices, tessellator, screen.getPreviewTexture().getTextureView(), screen.previewRenderType);
        } else {
            Render.renderBlack(matrices, tessellator, screen.renderType);
        }
        matrices.popPose();
    }
}

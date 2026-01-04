package com.dreamdisplays.mixins;

import com.dreamdisplays.Initializer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to hide crosshair when screen display is active and player focus is on the display.
 */
@Mixin(Gui.class)
@NullMarked
public class Crosshair {

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    public void renderCrosshair(
            GuiGraphics guiGraphics,
            DeltaTracker deltaTracker,
            CallbackInfo ci
    ) {
        if (Initializer.isOnScreen) {
            ci.cancel();
        }
    }
}

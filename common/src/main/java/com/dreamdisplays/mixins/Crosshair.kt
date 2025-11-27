package com.dreamdisplays.mixins

import com.dreamdisplays.Initializer
import net.minecraft.client.DeltaTracker
import net.minecraft.client.gui.Gui
import net.minecraft.client.gui.GuiGraphics
import org.jspecify.annotations.NullMarked
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

/**
 * Mixin for hiding the crosshair when player is focused on a display screen.
 */
@Mixin(Gui::class)
@NullMarked
class Crosshair {
    @Inject(method = ["renderCrosshair"], at = [At("HEAD")], cancellable = true)
    fun renderCrosshair(@Suppress("UNUSED_PARAMETER") guiGraphics: GuiGraphics, @Suppress("UNUSED_PARAMETER") deltaTracker: DeltaTracker, ci: CallbackInfo) {
        if (Initializer.isOnScreen) {
            ci.cancel()
        }
    }
}

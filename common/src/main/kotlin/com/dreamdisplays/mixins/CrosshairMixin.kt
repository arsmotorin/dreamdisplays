package com.dreamdisplays.mixins

import com.dreamdisplays.ModInitializer.isOnScreen
import net.minecraft.client.gui.Gui
import org.jspecify.annotations.NullMarked
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

/**
 * Mixin to hide crosshair when screen display is active and player focus is on the display.
 */
@Mixin(Gui::class)
@NullMarked
class CrosshairMixin {
    @Inject(method = ["renderCrosshair"], at = [At("HEAD")], cancellable = true)
    fun renderCrosshair(
        ci: CallbackInfo,
    ) {
        if (isOnScreen) {
            ci.cancel()
        }
    }
}

package com.dreamdisplays.mixins

import com.dreamdisplays.Initializer
import net.minecraft.client.DeltaTracker
import net.minecraft.client.gui.Gui
import net.minecraft.client.gui.GuiGraphics
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Suppress("UNUSED")
@Mixin(Gui::class)
open class Crosshair {

    @Inject(method = ["renderCrosshair"], at = [At("HEAD")], cancellable = true)
    open fun renderCrosshair(
        guiGraphics: GuiGraphics,
        deltaTracker: DeltaTracker,
        ci: CallbackInfo
    ) {
        if (Initializer.isOnScreen) {
            ci.cancel()
        }
    }
}

package com.dreamdisplays.displays.settings.managers

import com.dreamdisplays.displays.settings.widgets.ButtonWidget
import net.minecraft.client.gui.components.AbstractWidget
import org.jspecify.annotations.NullMarked
import kotlin.math.min

/**
 * Handles layout and positioning of configuration screen widgets.
 */
@NullMarked
object LayoutManager {

    fun placeSliderWithReset(
        displayWidth: Int,
        vCH: Int,
        maxSW: Int,
        cY: Int,
        slider: AbstractWidget,
        resetButton: ButtonWidget,
    ) {
        slider.x = displayWidth / 2 + maxSW / 2 - 80 - vCH - 5
        slider.y = cY
        slider.setHeight(vCH)
        slider.setWidth(80)

        resetButton.x = displayWidth / 2 + maxSW / 2 - vCH
        resetButton.y = cY
        resetButton.setHeight(vCH)
        resetButton.setWidth(vCH)
    }

    fun calculatePreviewDimensions(
        maxWidth: Int,
        maxHeight: Int,
        videoWidth: Int,
        videoHeight: Int,
    ): Pair<Int, Int> {
        var width = maxWidth
        val height = min(
            ((videoHeight.toDouble() / videoWidth) * width).toInt().toDouble(),
            maxHeight.toDouble()
        ).toInt()
        width = ((videoWidth.toDouble() / videoHeight) * height).toInt()

        return Pair(width, height)
    }
}
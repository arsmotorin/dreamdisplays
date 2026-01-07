package com.dreamdisplays.screen.settings

import com.dreamdisplays.screen.settings.widgets.Button
import net.minecraft.client.gui.components.AbstractWidget
import org.jspecify.annotations.NullMarked

/**
 * Handles layout and positioning of configuration screen widgets.
 */
@NullMarked
object ConfigurationLayout {

    fun placeSliderWithReset(
        screenWidth: Int,
        vCH: Int,
        maxSW: Int,
        cY: Int,
        slider: AbstractWidget,
        resetButton: Button
    ) {
        slider.x = screenWidth / 2 + maxSW / 2 - 80 - vCH - 5
        slider.y = cY
        slider.setHeight(vCH)
        slider.setWidth(80)

        resetButton.x = screenWidth / 2 + maxSW / 2 - vCH
        resetButton.y = cY
        resetButton.setHeight(vCH)
        resetButton.setWidth(vCH)
    }

    fun calculatePreviewDimensions(
        maxWidth: Int,
        maxHeight: Int,
        videoWidth: Int,
        videoHeight: Int
    ): Pair<Int, Int> {
        var width = maxWidth
        val height = kotlin.math.min(
            ((videoHeight.toDouble() / videoWidth) * width).toInt().toDouble(),
            maxHeight.toDouble()
        ).toInt()
        width = ((videoWidth.toDouble() / videoHeight) * height).toInt()

        return Pair(width, height)
    }
}

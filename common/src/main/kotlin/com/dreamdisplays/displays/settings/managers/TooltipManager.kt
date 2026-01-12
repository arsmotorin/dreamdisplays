package com.dreamdisplays.displays.settings.managers

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import org.jspecify.annotations.NullMarked

/**
 * Generates tooltips for configuration screen elements.
 */
@NullMarked
object TooltipManager {

    fun createVolumeTooltip(currentVolume: Double): List<Component> {
        return listOf(
            Component.translatable(
                "dreamdisplays.button.volume.tooltip.1"
            ).withStyle { style: Style? -> style!!.withColor(ChatFormatting.WHITE).withBold(true) },
            Component.translatable(
                "dreamdisplays.button.volume.tooltip.2"
            ).withStyle { style: Style? -> style!!.withColor(ChatFormatting.GRAY) },
            Component.translatable(
                "dreamdisplays.button.volume.tooltip.3"
            ).withStyle { style: Style? -> style!!.withColor(ChatFormatting.GRAY) },
            Component.translatable(
                "dreamdisplays.button.volume.tooltip.4",
                (currentVolume * 200).toInt()
            ).withStyle { style: Style? -> style!!.withColor(ChatFormatting.GOLD) }
        )
    }

    fun createRenderDistanceTooltip(currentDistance: Int): List<Component> {
        return listOf(
            Component.translatable(
                "dreamdisplays.button.render-distance.tooltip.1"
            ).withStyle { style: Style? -> style!!.withColor(ChatFormatting.WHITE).withBold(true) },
            Component.translatable(
                "dreamdisplays.button.render-distance.tooltip.2"
            ).withStyle { style: Style? -> style!!.withColor(ChatFormatting.GRAY) },
            Component.translatable(
                "dreamdisplays.button.render-distance.tooltip.3"
            ).withStyle { style: Style? -> style!!.withColor(ChatFormatting.GRAY) },
            Component.translatable(
                "dreamdisplays.button.render-distance.tooltip.4"
            ),
            Component.translatable(
                "dreamdisplays.button.render-distance.tooltip.5"
            ).withStyle { style: Style? -> style!!.withColor(ChatFormatting.DARK_GRAY) },
            Component.translatable(
                "dreamdisplays.button.render-distance.tooltip.6"
            ).withStyle { style: Style? -> style!!.withColor(ChatFormatting.DARK_GRAY) },
            Component.translatable(
                "dreamdisplays.button.render-distance.tooltip.7"
            ),
            Component.translatable(
                "dreamdisplays.button.render-distance.tooltip.8",
                currentDistance
            ).withStyle { style: Style? -> style!!.withColor(ChatFormatting.GOLD) }
        )
    }

    fun createQualityTooltip(currentQuality: String, isHighQuality: Boolean): MutableList<Component> {
        val tooltip = ArrayList<Component>(
            listOf(
                Component.translatable(
                    "dreamdisplays.button.quality.tooltip.1"
                ).withStyle { style: Style? ->
                    style!!.withColor(ChatFormatting.WHITE).withBold(true)
                },
                Component.translatable(
                    "dreamdisplays.button.quality.tooltip.2"
                ).withStyle { style: Style? -> style!!.withColor(ChatFormatting.GRAY) },
                Component.translatable(
                    "dreamdisplays.button.quality.tooltip.3"
                ),
                Component.translatable(
                    "dreamdisplays.button.quality.tooltip.4",
                    currentQuality
                ).withStyle { style: Style? -> style!!.withColor(ChatFormatting.GOLD) }
            )
        )

        if (isHighQuality) {
            tooltip.add(
                Component.translatable(
                    "dreamdisplays.button.quality.tooltip.5"
                ).withStyle { style: Style? -> style!!.withColor(ChatFormatting.YELLOW) }
            )
        }

        return tooltip
    }

    fun createBrightnessTooltip(currentBrightness: Int): List<Component> {
        return listOf(
            Component.translatable(
                "dreamdisplays.button.brightness.tooltip.1"
            ).withStyle { style: Style? -> style!!.withColor(ChatFormatting.WHITE).withBold(true) },
            Component.translatable(
                "dreamdisplays.button.brightness.tooltip.2"
            ).withStyle { style: Style? -> style!!.withColor(ChatFormatting.GRAY) },
            Component.translatable(
                "dreamdisplays.button.brightness.tooltip.3",
                currentBrightness
            ).withStyle { style: Style? -> style!!.withColor(ChatFormatting.GOLD) }
        )
    }

    fun createSyncTooltip(isSyncEnabled: Boolean): List<Component> {
        return listOf(
            Component.translatable(
                "dreamdisplays.button.synchronization.tooltip.1"
            ).withStyle { style: Style? -> style!!.withColor(ChatFormatting.WHITE).withBold(true) },
            Component.translatable(
                "dreamdisplays.button.synchronization.tooltip.2"
            ).withStyle { style: Style? -> style!!.withColor(ChatFormatting.GRAY) },
            Component.translatable(
                "dreamdisplays.button.synchronization.tooltip.3"
            ).withStyle { style: Style? -> style!!.withColor(ChatFormatting.GRAY) },
            Component.translatable(
                "dreamdisplays.button.synchronization.tooltip.4"
            ),
            Component.translatable(
                "dreamdisplays.button.synchronization.tooltip.5",
                if (isSyncEnabled)
                    Component.translatable("dreamdisplays.button.enabled")
                else
                    Component.translatable("dreamdisplays.button.disabled")
            ).withStyle { style: Style? -> style!!.withColor(ChatFormatting.GOLD) }
        )
    }

    fun createDeleteTooltip(): List<Component> {
        return listOf(
            Component.translatable(
                "dreamdisplays.button.delete.tooltip.1"
            ).withStyle { style: Style? -> style!!.withColor(ChatFormatting.WHITE).withBold(true) },
            Component.translatable(
                "dreamdisplays.button.delete.tooltip.2"
            ).withStyle { style: Style? -> style!!.withColor(ChatFormatting.GRAY) }
        )
    }

    /**
     * Creates tooltip for report button
     */
    fun createReportTooltip(): List<Component> {
        return listOf(
            Component.translatable(
                "dreamdisplays.button.report.tooltip.1"
            ).withStyle { style: Style? -> style!!.withColor(ChatFormatting.WHITE).withBold(true) },
            Component.translatable(
                "dreamdisplays.button.report.tooltip.2"
            ).withStyle { style: Style? -> style!!.withColor(ChatFormatting.GRAY) }
        )
    }

    fun createErrorComponents(): List<Component> {
        return listOf(
            Component.translatable(
                "dreamdisplays.error.loadingerror.1"
            ).withStyle { style: Style? -> style!!.withColor(0xff0000) },
            Component.translatable(
                "dreamdisplays.error.loadingerror.2"
            ).withStyle { style: Style? -> style!!.withColor(0xff0000) },
            Component.translatable(
                "dreamdisplays.error.loadingerror.3"
            ).withStyle { style: Style? -> style!!.withColor(0xff0000) },
            Component.translatable(
                "dreamdisplays.error.loadingerror.4"
            ).withStyle { style: Style? -> style!!.withColor(0xff0000) },
            Component.translatable(
                "dreamdisplays.error.loadingerror.5"
            ).withStyle { style: Style? -> style!!.withColor(0xff0000) }
        )
    }
}
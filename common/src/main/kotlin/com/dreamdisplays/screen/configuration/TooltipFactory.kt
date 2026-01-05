package com.dreamdisplays.screen.configuration

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component

/**
 * Factory for creating tooltips used in the configuration screen.
 */
object TooltipFactory {

    fun volumeTooltip(currentValue: Double): List<Component> = listOf(
        Component.translatable("dreamdisplays.button.volume.tooltip.1")
            .withStyle { it.withColor(ChatFormatting.WHITE).withBold(true) },
        Component.translatable("dreamdisplays.button.volume.tooltip.2")
            .withStyle { it.withColor(ChatFormatting.GRAY) },
        Component.translatable("dreamdisplays.button.volume.tooltip.3")
            .withStyle { it.withColor(ChatFormatting.GRAY) },
        Component.translatable("dreamdisplays.button.volume.tooltip.4", (currentValue * 200).toInt())
            .withStyle { it.withColor(ChatFormatting.GOLD) }
    )

    fun renderDistanceTooltip(currentValue: Double): List<Component> = listOf(
        Component.translatable("dreamdisplays.button.render-distance.tooltip.1")
            .withStyle { it.withColor(ChatFormatting.WHITE).withBold(true) },
        Component.translatable("dreamdisplays.button.render-distance.tooltip.2")
            .withStyle { it.withColor(ChatFormatting.GRAY) },
        Component.translatable("dreamdisplays.button.render-distance.tooltip.3")
            .withStyle { it.withColor(ChatFormatting.GRAY) },
        Component.translatable("dreamdisplays.button.render-distance.tooltip.4"),
        Component.translatable("dreamdisplays.button.render-distance.tooltip.5")
            .withStyle { it.withColor(ChatFormatting.DARK_GRAY) },
        Component.translatable("dreamdisplays.button.render-distance.tooltip.6")
            .withStyle { it.withColor(ChatFormatting.DARK_GRAY) },
        Component.translatable("dreamdisplays.button.render-distance.tooltip.7"),
        Component.translatable("dreamdisplays.button.render-distance.tooltip.8", (currentValue * (128 - 24) + 24).toInt())
            .withStyle { it.withColor(ChatFormatting.GOLD) }
    )

    fun qualityTooltip(qualityValue: String, showHighQualityWarning: Boolean): List<Component> {
        val base = mutableListOf(
            Component.translatable("dreamdisplays.button.quality.tooltip.1")
                .withStyle { it.withColor(ChatFormatting.WHITE).withBold(true) },
            Component.translatable("dreamdisplays.button.quality.tooltip.2")
                .withStyle { it.withColor(ChatFormatting.GRAY) },
            Component.translatable("dreamdisplays.button.quality.tooltip.3"),
            Component.translatable("dreamdisplays.button.quality.tooltip.4", qualityValue)
                .withStyle { it.withColor(ChatFormatting.GOLD) }
        )
        if (showHighQualityWarning) {
            base.add(
                Component.translatable("dreamdisplays.button.quality.tooltip.5")
                    .withStyle { it.withColor(ChatFormatting.YELLOW) }
            )
        }
        return base
    }

    fun brightnessTooltip(currentValue: Double): List<Component> = listOf(
        Component.translatable("dreamdisplays.button.brightness.tooltip.1")
            .withStyle { it.withColor(ChatFormatting.WHITE).withBold(true) },
        Component.translatable("dreamdisplays.button.brightness.tooltip.2")
            .withStyle { it.withColor(ChatFormatting.GRAY) },
        Component.translatable("dreamdisplays.button.brightness.tooltip.3", (currentValue * 200).toInt())
            .withStyle { it.withColor(ChatFormatting.GOLD) }
    )

    fun syncTooltip(isEnabled: Boolean): List<Component> = listOf(
        Component.translatable("dreamdisplays.button.synchronization.tooltip.1")
            .withStyle { it.withColor(ChatFormatting.WHITE).withBold(true) },
        Component.translatable("dreamdisplays.button.synchronization.tooltip.2")
            .withStyle { it.withColor(ChatFormatting.GRAY) },
        Component.translatable("dreamdisplays.button.synchronization.tooltip.3")
            .withStyle { it.withColor(ChatFormatting.GRAY) },
        Component.translatable("dreamdisplays.button.synchronization.tooltip.4"),
        Component.translatable(
            "dreamdisplays.button.synchronization.tooltip.5",
            if (isEnabled) Component.translatable("dreamdisplays.button.enabled")
            else Component.translatable("dreamdisplays.button.disabled")
        ).withStyle { it.withColor(ChatFormatting.GOLD) }
    )

    fun deleteTooltip(): List<Component> = listOf(
        Component.translatable("dreamdisplays.button.delete.tooltip.1")
            .withStyle { it.withColor(ChatFormatting.WHITE).withBold(true) },
        Component.translatable("dreamdisplays.button.delete.tooltip.2")
            .withStyle { it.withColor(ChatFormatting.GRAY) }
    )

    fun reportTooltip(): List<Component> = listOf(
        Component.translatable("dreamdisplays.button.report.tooltip.1")
            .withStyle { it.withColor(ChatFormatting.WHITE).withBold(true) },
        Component.translatable("dreamdisplays.button.report.tooltip.2")
            .withStyle { it.withColor(ChatFormatting.GRAY) }
    )

    fun errorMessages(): List<Component> = listOf(
        Component.translatable("dreamdisplays.error.loadingerror.1").withStyle { it.withColor(0xff0000) },
        Component.translatable("dreamdisplays.error.loadingerror.2").withStyle { it.withColor(0xff0000) },
        Component.translatable("dreamdisplays.error.loadingerror.3").withStyle { it.withColor(0xff0000) },
        Component.translatable("dreamdisplays.error.loadingerror.4").withStyle { it.withColor(0xff0000) },
        Component.translatable("dreamdisplays.error.loadingerror.5").withStyle { it.withColor(0xff0000) }
    )
}

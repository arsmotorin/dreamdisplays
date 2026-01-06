package com.dreamdisplays.screen.configuration

import net.minecraft.ChatFormatting.DARK_GRAY
import net.minecraft.ChatFormatting.GOLD
import net.minecraft.ChatFormatting.GRAY
import net.minecraft.ChatFormatting.WHITE
import net.minecraft.ChatFormatting.YELLOW
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Component.translatable

/**
 * Factory for creating tooltips used in the configuration screen.
 */
object TooltipFactory {

    fun volumeTooltip(currentValue: Double): List<Component> = listOf(
        translatable("dreamdisplays.button.volume.tooltip.1")
            .withStyle { it.withColor(WHITE).withBold(true) },
        translatable("dreamdisplays.button.volume.tooltip.2")
            .withStyle { it.withColor(GRAY) },
        translatable("dreamdisplays.button.volume.tooltip.3")
            .withStyle { it.withColor(GRAY) },
        translatable("dreamdisplays.button.volume.tooltip.4", (currentValue * 200).toInt())
            .withStyle { it.withColor(GOLD) }
    )

    fun renderDistanceTooltip(currentValue: Double): List<Component> = listOf(
        translatable("dreamdisplays.button.render-distance.tooltip.1")
            .withStyle { it.withColor(WHITE).withBold(true) },
        translatable("dreamdisplays.button.render-distance.tooltip.2")
            .withStyle { it.withColor(GRAY) },
        translatable("dreamdisplays.button.render-distance.tooltip.3")
            .withStyle { it.withColor(GRAY) },
        translatable("dreamdisplays.button.render-distance.tooltip.4"),
        translatable("dreamdisplays.button.render-distance.tooltip.5")
            .withStyle { it.withColor(DARK_GRAY) },
        translatable("dreamdisplays.button.render-distance.tooltip.6")
            .withStyle { it.withColor(DARK_GRAY) },
        translatable("dreamdisplays.button.render-distance.tooltip.7"),
        translatable(
            "dreamdisplays.button.render-distance.tooltip.8",
            (currentValue * (128 - 24) + 24).toInt()
        )
            .withStyle { it.withColor(GOLD) }
    )

    fun qualityTooltip(qualityValue: String, showHighQualityWarning: Boolean): List<Component> {
        val base = mutableListOf(
            translatable("dreamdisplays.button.quality.tooltip.1")
                .withStyle { it.withColor(WHITE).withBold(true) },
            translatable("dreamdisplays.button.quality.tooltip.2")
                .withStyle { it.withColor(GRAY) },
            translatable("dreamdisplays.button.quality.tooltip.3"),
            translatable("dreamdisplays.button.quality.tooltip.4", qualityValue)
                .withStyle { it.withColor(GOLD) }
        )
        if (showHighQualityWarning) {
            base.add(
                translatable("dreamdisplays.button.quality.tooltip.5")
                    .withStyle { it.withColor(YELLOW) }
            )
        }
        return base
    }

    fun brightnessTooltip(currentValue: Double): List<Component> = listOf(
        translatable("dreamdisplays.button.brightness.tooltip.1")
            .withStyle { it.withColor(WHITE).withBold(true) },
        translatable("dreamdisplays.button.brightness.tooltip.2")
            .withStyle { it.withColor(GRAY) },
        translatable("dreamdisplays.button.brightness.tooltip.3", (currentValue * 200).toInt())
            .withStyle { it.withColor(GOLD) }
    )

    fun syncTooltip(isEnabled: Boolean): List<Component> = listOf(
        translatable("dreamdisplays.button.synchronization.tooltip.1")
            .withStyle { it.withColor(WHITE).withBold(true) },
        translatable("dreamdisplays.button.synchronization.tooltip.2")
            .withStyle { it.withColor(GRAY) },
        translatable("dreamdisplays.button.synchronization.tooltip.3")
            .withStyle { it.withColor(GRAY) },
        translatable("dreamdisplays.button.synchronization.tooltip.4"),
        translatable(
            "dreamdisplays.button.synchronization.tooltip.5",
            if (isEnabled) translatable("dreamdisplays.button.enabled")
            else translatable("dreamdisplays.button.disabled")
        ).withStyle { it.withColor(GOLD) }
    )

    fun deleteTooltip(): List<Component> = listOf(
        translatable("dreamdisplays.button.delete.tooltip.1")
            .withStyle { it.withColor(WHITE).withBold(true) },
        translatable("dreamdisplays.button.delete.tooltip.2")
            .withStyle { it.withColor(GRAY) }
    )

    fun reportTooltip(): List<Component> = listOf(
        translatable("dreamdisplays.button.report.tooltip.1")
            .withStyle { it.withColor(WHITE).withBold(true) },
        translatable("dreamdisplays.button.report.tooltip.2")
            .withStyle { it.withColor(GRAY) }
    )

    fun errorMessages(): List<Component> = listOf(
        translatable("dreamdisplays.error.loadingerror.1").withStyle { it.withColor(0xff0000) },
        translatable("dreamdisplays.error.loadingerror.2").withStyle { it.withColor(0xff0000) },
        translatable("dreamdisplays.error.loadingerror.3").withStyle { it.withColor(0xff0000) },
        translatable("dreamdisplays.error.loadingerror.4").withStyle { it.withColor(0xff0000) },
        translatable("dreamdisplays.error.loadingerror.5").withStyle { it.withColor(0xff0000) }
    )
}

package com.dreamdisplays.downloader

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.jspecify.annotations.NullMarked
import kotlin.math.roundToInt

/**
 * Will be removed in 2.0.0 version and replaced with FFmpeg solution.
 */
@NullMarked
class Menu(private val parentMenu: Screen) : Screen(
    Component.nullToEmpty("Dream Displays downloads GStreamer for display support")
) {

    override fun render(
        graphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float,
    ) {
        super.render(graphics, mouseX, mouseY, partialTick)
        val cx = width / 2f
        val cy = height / 2f

        val progressBarHeight = 14f
        val progressBarWidth = width / 3f

        val matrix = graphics.pose()

        // Draw progress bar background
        matrix.pushMatrix()
        matrix.translate(cx, cy)
        matrix.translate(
            -progressBarWidth / 2f,
            -progressBarHeight / 2f
        )
        graphics.fill(
            0,
            0,
            progressBarWidth.toInt(),
            progressBarHeight.toInt(),
            -1
        )
        graphics.fill(
            2,
            2,
            (progressBarWidth - 2).toInt(),
            (progressBarHeight - 2).toInt(),
            -16777215
        )
        graphics.fill(
            4,
            4,
            ((progressBarWidth - 4) * Listener.progress).toInt(),
            (progressBarHeight - 4).toInt(),
            -1
        )
        matrix.popMatrix()

        val text = listOf(
            Listener.task,
            "${(Listener.progress * 100).roundToInt() % 100}%"
        )

        val oSet = (font.lineHeight / 2) + ((font.lineHeight + 2) * (text.size + 2)) + 4

        matrix.pushMatrix()
        matrix.translate(cx, cy - oSet)

        graphics.drawString(
            font,
            title.string,
            -(font.width(title.string) / 2),
            0,
            0xFFFFFF,
            true
        )

        text.forEachIndexed { index, s ->
            if (index == 1) {
                matrix.translate(0f, (font.lineHeight + 2).toFloat())
            }

            matrix.translate(0f, (font.lineHeight + 2).toFloat())
            graphics.drawString(
                font,
                s,
                -(font.width(s) / 2),
                0,
                0xFFFFFF,
                true
            )
        }
        matrix.popMatrix()
    }

    override fun tick() {
        if (Listener.isDone && !Listener.isFailed) {
            Minecraft.getInstance().setScreen(parentMenu)
        }
    }

    override fun shouldCloseOnEsc(): Boolean {
        return false
    }
}

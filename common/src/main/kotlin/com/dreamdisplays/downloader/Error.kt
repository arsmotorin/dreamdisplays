package com.dreamdisplays.downloader

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.jspecify.annotations.NullMarked

/**
 * Will be removed in 2.0.0 version and replaced with FFmpeg solution.
 */
@NullMarked
class Error(
    private val parent: Screen,
    private val errorMessage: String
) : Screen(Component.nullToEmpty("Error while downloading GStreamer")) {

    // Initializes the screen and adds the "Continue" button
    override fun init() {
        super.init()

        addRenderableWidget(
            Button.builder(
                Component.nullToEmpty("Continue")
            ) { minecraft.setScreen(parent) }
                .bounds(width / 2 - 50, height / 2 + 40, 100, 20)
                .build()
        )
    }

    // Renders the error screen with title and error message
    override fun render(
        context: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        delta: Float
    ) {
        renderBackground(context, mouseX, mouseY, delta)

        val titleText = title.string
        val titleWidth = font.width(titleText)

        // Centered title
        context.drawString(
            font,
            titleText,
            ((width - titleWidth) / 2f).toInt(),
            (height / 2f - 40f).toInt(),
            0xFF5555,
            true
        )

        // Error message
        val msgWidth = font.width(errorMessage)
        context.drawString(
            font,
            errorMessage,
            ((width - msgWidth) / 2f).toInt(),
            (height / 2f - 20f).toInt(),
            0xFF5555,
            true
        )

        super.render(context, mouseX, mouseY, delta)
    }

    // Disable closing on ESC key
    override fun shouldCloseOnEsc(): Boolean {
        return false
    }
}

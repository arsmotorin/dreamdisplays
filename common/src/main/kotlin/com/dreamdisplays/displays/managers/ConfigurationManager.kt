package com.dreamdisplays.displays.managers

import com.dreamdisplays.ModInitializer
import com.dreamdisplays.displays.DisplayScreen
import com.dreamdisplays.displays.settings.managers.LayoutManager
import com.dreamdisplays.displays.settings.managers.TooltipManager
import com.dreamdisplays.displays.settings.managers.WidgetManager
import com.dreamdisplays.displays.settings.utils.QualityConverter
import com.dreamdisplays.displays.settings.widgets.ButtonWidget
import com.dreamdisplays.displays.settings.widgets.SliderWidget
import com.dreamdisplays.displays.settings.widgets.TimelineWidget
import com.dreamdisplays.displays.settings.widgets.ToggleWidget
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Renderable
import net.minecraft.client.gui.components.WidgetSprites
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import org.jspecify.annotations.NullMarked
import kotlin.math.abs
import kotlin.math.floor

/**
 * Configuration for display settings.
 */
@NullMarked
class ConfigurationManager private constructor() : Screen(Component.translatable("dreamdisplays.ui.title")) {
    // Widget references
    var volume: SliderWidget? = null
    var renderD: SliderWidget? = null
    var quality: SliderWidget? = null
    var brightness: SliderWidget? = null
    var sync: ToggleWidget? = null
    var backButton: ButtonWidget? = null
    var forwardButton: ButtonWidget? = null
    var pauseButton: ButtonWidget? = null
    var timeline: TimelineWidget? = null
    var renderDReset: ButtonWidget? = null
    var qualityReset: ButtonWidget? = null
    var brightnessReset: ButtonWidget? = null
    var volumeReset: ButtonWidget? = null
    var syncReset: ButtonWidget? = null
    var deleteButton: ButtonWidget? = null
    var reportButton: ButtonWidget? = null

    var display: DisplayScreen? = null

    override fun init() {
        val currentDisplay = display ?: return

        // Create widgets
        volume = WidgetManager.createVolumeSlider(currentDisplay)
        backButton = WidgetManager.createBackButton(currentDisplay)
        forwardButton = WidgetManager.createForwardButton(currentDisplay)
        pauseButton = WidgetManager.createPauseButton(currentDisplay)
        timeline = WidgetManager.createTimeline(currentDisplay)

        renderD = WidgetManager.createRenderDistanceSlider(currentDisplay) {
            DisplayManager.saveDisplayData(currentDisplay)
        }
        renderDReset = WidgetManager.createRenderDistanceResetButton(currentDisplay, renderD!!) {
            DisplayManager.saveDisplayData(currentDisplay)
        }

        quality = WidgetManager.createQualitySlider(currentDisplay)
        qualityReset = WidgetManager.createQualityResetButton(currentDisplay, quality!!)

        brightness = WidgetManager.createBrightnessSlider(currentDisplay)
        brightnessReset = WidgetManager.createBrightnessResetButton(currentDisplay, brightness)

        volumeReset = WidgetManager.createVolumeResetButton(currentDisplay, volume)

        val tempSync = WidgetManager.createSyncToggle(currentDisplay, null)
        syncReset = WidgetManager.createSyncResetButton(currentDisplay, tempSync)
        sync = tempSync.also {
            try {
                val resetButtonField = it.javaClass.getDeclaredField("resetButton")
                resetButtonField.isAccessible = true
                resetButtonField.set(it, syncReset)
            } catch (_: Exception) {
            }
        }

        deleteButton = WidgetManager.createDeleteButton(currentDisplay) { onClose() }

        if (ModInitializer.isReportingEnabled) {
            reportButton = WidgetManager.createReportButton(currentDisplay) { onClose() }
        }

        // Set initial widget states
        sync!!.active = currentDisplay.owner
        brightness?.let { it.active = !currentDisplay.isSync || currentDisplay.owner }
        deleteButton!!.active = currentDisplay.owner

        // Apply button sprites
        val sprites = WidgetSprites(
            Identifier.fromNamespaceAndPath(ModInitializer.MOD_ID, "widgets/red_button"),
            Identifier.fromNamespaceAndPath(ModInitializer.MOD_ID, "widgets/red_button_disabled"),
            Identifier.fromNamespaceAndPath(ModInitializer.MOD_ID, "widgets/red_button_highlighted")
        )
        deleteButton!!.setSprites(sprites)
        reportButton?.setSprites(sprites)

        // Register widgets
        volume?.let { addRenderableWidget(it) }
        addRenderableWidget(backButton!!)
        addRenderableWidget(forwardButton!!)
        timeline?.let { addRenderableWidget(it) }
        addRenderableWidget(pauseButton!!)
        addRenderableWidget(renderD!!)
        addRenderableWidget(quality!!)
        addRenderableWidget(qualityReset!!)
        addRenderableWidget(brightness!!)
        addRenderableWidget(brightnessReset!!)
        addRenderableWidget(renderDReset!!)
        addRenderableWidget(volumeReset!!)
        addRenderableWidget(sync!!)
        addRenderableWidget(syncReset!!)
        addRenderableWidget(deleteButton!!)
        reportButton?.let { addRenderableWidget(it) }
    }

    private fun renderTooltipIfHovered(
        guiGraphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        elementX: Int,
        elementY: Int,
        elementWidth: Int,
        elementHeight: Int,
        tooltip: List<Component>,
    ) {
        if (mouseX >= elementX && mouseX <= elementX + elementWidth &&
            mouseY >= elementY && mouseY <= elementY + elementHeight
        ) {
            guiGraphics.setComponentTooltipForNextFrame(
                Minecraft.getInstance().font,
                tooltip.toMutableList(),
                mouseX,
                mouseY
            )
        }
    }

    private fun renderErrorDisplay(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        // Disable all widgets when error occurs
        listOf(
            volume, renderD, quality, sync, backButton, forwardButton,
            pauseButton, timeline, renderDReset, qualityReset, volumeReset, syncReset
        )
            .forEach { it?.active = false }

        val errorComponents = TooltipManager.createErrorComponents()

        var yP = (this.height.toDouble() / 2 -
                ((font.lineHeight + 2).toDouble() * errorComponents.size) / 2).toInt()

        for (component in errorComponents) {
            guiGraphics.drawString(
                font,
                component,
                this.width / 2 - font.width(component) / 2,
                2 + font.lineHeight.let { yP += it; yP },
                -0x1,
                true
            )
        }

        deleteButton?.render(guiGraphics, mouseX, mouseY, delta)
        reportButton?.render(guiGraphics, mouseX, mouseY, delta)
    }

    private fun updateWidgetStates() {
        val currentDisplay = display ?: return

        syncReset?.active = currentDisplay.owner && currentDisplay.isSync

        renderDReset?.let {
            it.active = currentDisplay.renderDistance != ModInitializer.config.defaultDistance
        }

        qualityReset?.active = currentDisplay.quality != "720"

        brightnessReset?.let { resetBtn ->
            brightness?.let { slider ->
                resetBtn.active = abs(slider.value - 0.5) > 0.01
            }
        }

        volumeReset?.let { resetBtn ->
            volume?.let { slider ->
                resetBtn.active = abs(slider.value - 0.5) > 0.01
            }
        }
    }

    // Renders the display configuration display
    override fun render(
        guiGraphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        delta: Float,
    ) {
        super.render(guiGraphics, mouseX, mouseY, delta)

        val currentDisplay = display ?: return
        val vCH = 21

        // Position delete and report buttons
        deleteButton?.let {
            it.x = 10
            it.y = this.height - vCH - 10
            it.setHeight(vCH)
            it.setWidth(vCH)
        }

        reportButton?.let {
            it.x = this.width - vCH - 10
            it.y = this.height - vCH - 10
            it.setHeight(vCH)
            it.setWidth(vCH)
        }

        // Handle error state
        if (currentDisplay.errored) {
            renderErrorDisplay(guiGraphics, mouseX, mouseY, delta)
            return
        }

        // Update widget states based on current display state
        updateWidgetStates()

        // Render header
        val headerText = Component.translatable("dreamdisplays.ui.title")
        val headerTextX = (this.width - font.width(headerText)) / 2
        val headerTextY = 15
        guiGraphics.drawString(font, headerText, headerTextX, headerTextY, -0x1, true)

        // Render display preview
        val maxSW = this.width / 3
        val (previewW, previewH) = LayoutManager.calculatePreviewDimensions(
            maxSW,
            (this.height / 3.5).toInt(),
            currentDisplay.getWidth().toInt(),
            currentDisplay.getHeight().toInt()
        )
        val previewX = this.width / 2 - previewW / 2
        var currentY = font.lineHeight + 15 * 2

        guiGraphics.fill(
            this.width / 2 - maxSW / 2,
            currentY,
            this.width / 2 + maxSW / 2,
            currentY + previewH,
            -0x1000000
        )
        guiGraphics.pose().pushMatrix()
        guiGraphics.pose().translate(0f, 0f)
        renderScreenPreview(guiGraphics, previewX, currentY, previewW, previewH)
        guiGraphics.pose().popMatrix()

        currentY += previewH + 5

        // Layout media controls
        layoutMediaControls(currentY, maxSW, vCH, currentDisplay)
        currentY += 10 + vCH

        // Layout sliders and settings
        layoutSettings(guiGraphics, mouseX, mouseY, currentY, maxSW, vCH, currentDisplay)

        // Render tooltips for delete/report buttons
        renderActionButtonTooltips(guiGraphics, mouseX, mouseY)

        // Render all children
        for (child in children()) {
            if (child is Renderable) {
                child.render(guiGraphics, mouseX, mouseY, delta)
            }
        }
    }

    private fun layoutMediaControls(currentY: Int, maxSW: Int, vCH: Int, currentScreen: DisplayScreen) {
        val isSyncedAndNotOwner = currentScreen.isSync && !currentScreen.owner

        backButton?.let {
            it.x = this.width / 2 - maxSW / 2
            it.y = currentY
            it.setHeight(vCH)
            it.setWidth(vCH)
            it.active = !isSyncedAndNotOwner
        }

        forwardButton?.let {
            it.x = this.width / 2 - maxSW / 2 + vCH + 5
            it.y = currentY
            it.setHeight(vCH)
            it.setWidth(vCH)
            it.active = !isSyncedAndNotOwner
        }

        timeline?.let {
            val timelineX = this.width / 2 - maxSW / 2 + (vCH + 5) * 2
            val timelineWidth = maxSW - (vCH + 5) * 2 - vCH - 5
            it.x = timelineX
            it.y = currentY
            it.setHeight(vCH)
            it.setWidth(timelineWidth)
            it.active = !isSyncedAndNotOwner
        }

        pauseButton?.let {
            it.x = this.width / 2 + maxSW / 2 - vCH
            it.y = currentY
            it.setHeight(vCH)
            it.setWidth(vCH)
            it.active = !isSyncedAndNotOwner
        }

        sync?.active = currentScreen.owner
        deleteButton?.active = currentScreen.owner
    }

    private fun layoutSettings(
        guiGraphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        startY: Int,
        maxSW: Int,
        vCH: Int,
        currentScreen: DisplayScreen,
    ): Int {
        var currentY = startY

        // Volume
        volume?.let { vol ->
            volumeReset?.let { reset ->
                LayoutManager.placeSliderWithReset(this.width, vCH, maxSW, currentY, vol, reset)

                val volumeText = Component.translatable("dreamdisplays.button.volume")
                val textX = this.width / 2 - maxSW / 2
                val textY = currentY + vCH / 2 - font.lineHeight / 2
                guiGraphics.drawString(font, volumeText, textX, textY, -0x1, true)

                val tooltip = TooltipManager.createVolumeTooltip(vol.value)
                renderTooltipIfHovered(
                    guiGraphics, mouseX, mouseY, textX, textY,
                    font.width(volumeText), font.lineHeight, tooltip
                )

                currentY += 5 + vCH
            }
        }

        // Render Distance
        renderD?.let { rd ->
            renderDReset?.let { reset ->
                LayoutManager.placeSliderWithReset(this.width, vCH, maxSW, currentY, rd, reset)

                val text = Component.translatable("dreamdisplays.button.render-distance")
                val textX = this.width / 2 - maxSW / 2
                val textY = currentY + vCH / 2 - font.lineHeight / 2
                guiGraphics.drawString(font, text, textX, textY, -0x1, true)

                val tooltip = TooltipManager.createRenderDistanceTooltip((rd.value * (128 - 24) + 24).toInt())
                renderTooltipIfHovered(
                    guiGraphics, mouseX, mouseY, textX, textY,
                    font.width(text), font.lineHeight, tooltip
                )

                currentY += 5 + vCH
            }
        }

        // Quality
        quality?.let { qual ->
            qualityReset?.let { reset ->
                LayoutManager.placeSliderWithReset(this.width, vCH, maxSW, currentY, qual, reset)

                val text = Component.translatable("dreamdisplays.button.quality")
                val textX = this.width / 2 - maxSW / 2
                val textY = currentY + vCH / 2 - font.lineHeight / 2
                guiGraphics.drawString(font, text, textX, textY, -0x1, true)

                val currentQuality = QualityConverter.toQuality(
                    (qual.value * currentScreen.getQualityList().size).toInt(),
                    currentScreen
                )
                val isHighQuality = currentScreen.quality.toInt() >= 1080
                val tooltip = TooltipManager.createQualityTooltip(currentQuality, isHighQuality)
                renderTooltipIfHovered(
                    guiGraphics, mouseX, mouseY, textX, textY,
                    font.width(text), font.lineHeight, tooltip
                )

                currentY += 5 + vCH
            }
        }

        // Brightness
        brightness?.let { bright ->
            brightnessReset?.let { reset ->
                LayoutManager.placeSliderWithReset(this.width, vCH, maxSW, currentY, bright, reset)

                val text = Component.translatable("dreamdisplays.button.brightness")
                val textX = this.width / 2 - maxSW / 2
                val textY = currentY + vCH / 2 - font.lineHeight / 2
                guiGraphics.drawString(font, text, textX, textY, -0x1, true)

                val tooltip = TooltipManager.createBrightnessTooltip(floor(bright.value * 200).toInt())
                renderTooltipIfHovered(
                    guiGraphics, mouseX, mouseY, textX, textY,
                    font.width(text), font.lineHeight, tooltip
                )

                currentY += 15 + vCH
            }
        }

        // Sync
        sync?.let { syncToggle ->
            syncReset?.let { reset ->
                LayoutManager.placeSliderWithReset(this.width, vCH, maxSW, currentY, syncToggle, reset)

                val text = Component.translatable("dreamdisplays.button.synchronization")
                val textX = this.width / 2 - maxSW / 2
                val textY = currentY + vCH / 2 - font.lineHeight / 2
                guiGraphics.drawString(font, text, textX, textY, -0x1, true)

                val tooltip = TooltipManager.createSyncTooltip(syncToggle.value)
                renderTooltipIfHovered(
                    guiGraphics, mouseX, mouseY, textX, textY,
                    font.width(text), font.lineHeight, tooltip
                )
            }
        }

        return currentY
    }

    private fun renderActionButtonTooltips(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        deleteButton?.let {
            val tooltip = TooltipManager.createDeleteTooltip()
            renderTooltipIfHovered(
                guiGraphics, mouseX, mouseY,
                it.x, it.y, it.getWidth(), it.getHeight(),
                tooltip
            )
        }

        reportButton?.let {
            val tooltip = TooltipManager.createReportTooltip()
            renderTooltipIfHovered(
                guiGraphics, mouseX, mouseY,
                it.x, it.y, it.getWidth(), it.getHeight(),
                tooltip
            )
        }
    }

    // Renders display screen preview
    private fun renderScreenPreview(
        guiGraphics: GuiGraphics,
        x: Int,
        y: Int,
        w: Int,
        h: Int,
    ) {
        val currentScreen = display ?: return

        if (currentScreen.isVideoStarted() && currentScreen.texture != null && currentScreen.textureId != null) {
            currentScreen.fitTexture()
            guiGraphics.blit(
                RenderPipelines.GUI_TEXTURED,
                currentScreen.textureId!!,
                x, y, 0f, 0f, w, h,
                currentScreen.textureWidth,
                currentScreen.textureHeight,
                currentScreen.textureWidth,
                currentScreen.textureHeight
            )
        }
    }

    // Sets the screen for the display config screen
    private fun attachScreen(screen: DisplayScreen) {
        this.display = screen
    }

    companion object {
        // Opens the display configuration screen
        fun open(screen: DisplayScreen) {
            val displayConfScreen = ConfigurationManager()
            displayConfScreen.attachScreen(screen)
            Minecraft.getInstance().setScreen(displayConfScreen)
        }
    }
}
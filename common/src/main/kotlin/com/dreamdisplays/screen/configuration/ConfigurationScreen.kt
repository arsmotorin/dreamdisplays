package com.dreamdisplays.screen.configuration

import com.dreamdisplays.Initializer
import com.dreamdisplays.net.c2s.Report
import com.dreamdisplays.net.common.Delete
import com.dreamdisplays.screen.Manager
import com.dreamdisplays.screen.Settings
import com.dreamdisplays.screen.widgets.Button
import com.dreamdisplays.screen.widgets.Slider
import com.dreamdisplays.screen.widgets.Toggle
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.WidgetSprites
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import org.jspecify.annotations.NullMarked
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import com.dreamdisplays.screen.Screen as DisplayScreen

/**
 * Configuration screen for display settings.
 * Refactored from Java to Kotlin with better code organization.
 */
@NullMarked
class ConfigurationScreen private constructor() : Screen(Component.translatable("dreamdisplays.ui.title")) {

    // Sliders
    private var volume: Slider? = null
    private var renderD: Slider? = null
    private var quality: Slider? = null
    private var brightness: Slider? = null
    private var sync: Toggle? = null

    // Navigation buttons
    private var backButton: Button? = null
    private var forwardButton: Button? = null
    private var pauseButton: Button? = null

    // Reset buttons
    private var renderDReset: Button? = null
    private var qualityReset: Button? = null
    private var brightnessReset: Button? = null
    private var volumeReset: Button? = null
    private var syncReset: Button? = null

    // Action buttons
    private var deleteButton: Button? = null
    private var reportButton: Button? = null

    // The display screen being configured
    @JvmField
    var screen: DisplayScreen? = null

    companion object {
        private const val WIDGET_HEIGHT = 25

        @JvmStatic
        fun open(screen: DisplayScreen) {
            val configScreen = ConfigurationScreen()
            configScreen.screen = screen
            Minecraft.getInstance().setScreen(configScreen)
        }
    }

    override fun init() {
        val displayScreen = screen ?: return

        initVolumeControls(displayScreen)
        initNavigationButtons(displayScreen)
        initRenderDistanceControls(displayScreen)
        initQualityControls(displayScreen)
        initBrightnessControls(displayScreen)
        initSyncControls(displayScreen)
        initActionButtons(displayScreen)

        registerWidgets()
    }

    private fun initVolumeControls(displayScreen: DisplayScreen) {
        volume = WidgetFactory.createVolumeSlider(displayScreen)
        volumeReset = WidgetFactory.createVolumeResetButton(displayScreen) { volume }
    }

    private fun initNavigationButtons(displayScreen: DisplayScreen) {
        backButton = WidgetFactory.createBackButton(displayScreen)
        forwardButton = WidgetFactory.createForwardButton(displayScreen)
        pauseButton = WidgetFactory.createPauseButton(displayScreen)
    }

    private fun initRenderDistanceControls(displayScreen: DisplayScreen) {
        renderD = WidgetFactory.createRenderDistanceSlider(displayScreen)
        renderDReset = WidgetFactory.createRenderDistanceResetButton(displayScreen) { renderD }
    }

    private fun initQualityControls(displayScreen: DisplayScreen) {
        quality = WidgetFactory.createQualitySlider(displayScreen, ::toQuality, ::fromQuality)
        qualityReset = WidgetFactory.createQualityResetButton(displayScreen, { quality }, ::toQuality, ::fromQuality)
    }

    private fun initBrightnessControls(displayScreen: DisplayScreen) {
        brightness = WidgetFactory.createBrightnessSlider(displayScreen)
        brightnessReset = WidgetFactory.createBrightnessResetButton(displayScreen) { brightness }

        brightness?.active = !displayScreen.isSync || displayScreen.owner
    }

    private fun initSyncControls(displayScreen: DisplayScreen) {
        sync = WidgetFactory.createSyncToggle(displayScreen) { syncReset }
        syncReset = WidgetFactory.createSyncResetButton(displayScreen) { sync }

        sync?.active = displayScreen.owner
    }

    private fun initActionButtons(displayScreen: DisplayScreen) {
        deleteButton = object : Button(
            0, 0, 0, 0, 64, 64,
            Identifier.fromNamespaceAndPath(Initializer.MOD_ID, "delete"), 2
        ) {
            override fun onPress() {
                Settings.removeDisplay(displayScreen.uuid)
                Manager.unregisterScreen(displayScreen)
                Initializer.sendPacket(Delete(displayScreen.uuid))
                onClose()
            }
        }
        deleteButton?.active = displayScreen.owner

        if (Initializer.isReportingEnabled) {
            reportButton = object : Button(
                0, 0, 0, 0, 64, 64,
                Identifier.fromNamespaceAndPath(Initializer.MOD_ID, "report"), 2
            ) {
                override fun onPress() {
                    Initializer.sendPacket(Report(displayScreen.uuid))
                    onClose()
                }
            }
        }

        val redButtonSprites = WidgetSprites(
            Identifier.fromNamespaceAndPath(Initializer.MOD_ID, "widgets/red_button"),
            Identifier.fromNamespaceAndPath(Initializer.MOD_ID, "widgets/red_button_disabled"),
            Identifier.fromNamespaceAndPath(Initializer.MOD_ID, "widgets/red_button_highlighted")
        )

        deleteButton?.setSprites(redButtonSprites)
        reportButton?.setSprites(redButtonSprites)
    }

    private fun registerWidgets() {
        volume?.let { addRenderableWidget(it) }
        backButton?.let { addRenderableWidget(it) }
        forwardButton?.let { addRenderableWidget(it) }
        pauseButton?.let { addRenderableWidget(it) }
        renderD?.let { addRenderableWidget(it) }
        quality?.let { addRenderableWidget(it) }
        qualityReset?.let { addRenderableWidget(it) }
        brightness?.let { addRenderableWidget(it) }
        brightnessReset?.let { addRenderableWidget(it) }
        renderDReset?.let { addRenderableWidget(it) }
        volumeReset?.let { addRenderableWidget(it) }
        sync?.let { addRenderableWidget(it) }
        syncReset?.let { addRenderableWidget(it) }
        deleteButton?.let { addRenderableWidget(it) }
        reportButton?.let { addRenderableWidget(it) }
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        val displayScreen = screen ?: return

        val maxSW = width / 3
        var cY = font.lineHeight + 15 * 2

        val sH = renderScreenPreview(guiGraphics, displayScreen, maxSW, cY)
        cY += sH + 5

        positionNavigationButtons(displayScreen, maxSW, cY)
        cY += WIDGET_HEIGHT + 10

        renderHeader(guiGraphics)

        if (displayScreen.errored) {
            renderErrorState(guiGraphics, mouseX, mouseY, delta)
            return
        }

        updateWidgetStates(displayScreen)

        positionActionButtons()

        renderSettingRows(guiGraphics, mouseX, mouseY, displayScreen, maxSW, cY)

        renderActionButtonTooltips(guiGraphics, mouseX, mouseY)

        super.render(guiGraphics, mouseX, mouseY, delta)
    }

    private fun positionActionButtons() {
        deleteButton?.apply {
            x = 10
            y = height - WIDGET_HEIGHT - 10
            width = WIDGET_HEIGHT
            height = WIDGET_HEIGHT
        }

        reportButton?.apply {
            x = width - WIDGET_HEIGHT - 10
            y = height - WIDGET_HEIGHT - 10
            width = WIDGET_HEIGHT
            height = WIDGET_HEIGHT
        }
    }

    private fun positionNavigationButtons(displayScreen: DisplayScreen, containerWidth: Int, cY: Int) {
        val isActive = !(displayScreen.isSync && !displayScreen.owner)

        backButton?.apply {
            x = width / 2 - containerWidth / 2
            y = cY
            width = WIDGET_HEIGHT
            height = WIDGET_HEIGHT
            active = isActive
        }

        forwardButton?.apply {
            x = width / 2 - containerWidth / 2 + WIDGET_HEIGHT + 5
            y = cY
            width = WIDGET_HEIGHT
            height = WIDGET_HEIGHT
            active = isActive
        }

        pauseButton?.apply {
            x = width / 2 + containerWidth / 2 - WIDGET_HEIGHT
            y = cY
            width = WIDGET_HEIGHT
            height = WIDGET_HEIGHT
            active = isActive
        }
    }

    private fun renderScreenPreview(guiGraphics: GuiGraphics, displayScreen: DisplayScreen, maxSW: Int, cY: Int): Int {
        var sW = maxSW
        val sH = min(
            ((displayScreen.getHeight() / displayScreen.getWidth()) * sW).toInt(),
            (height / 3.5).toInt()
        )
        sW = ((displayScreen.getWidth() / displayScreen.getHeight()) * sH).toInt()
        val sX = width / 2 - sW / 2

        guiGraphics.fill(width / 2 - maxSW / 2, cY, width / 2 + maxSW / 2, cY + sH, 0xff000000.toInt())

        if (displayScreen.isVideoStarted() && displayScreen.texture != null && displayScreen.textureId != null) {
            displayScreen.fitTexture()
            guiGraphics.blit(
                RenderPipelines.GUI_TEXTURED,
                displayScreen.textureId!!,
                sX, cY,
                0f, 0f,
                sW, sH,
                displayScreen.textureWidth, displayScreen.textureHeight,
                displayScreen.textureWidth, displayScreen.textureHeight
            )
        }

        return sH
    }

    private fun renderHeader(guiGraphics: GuiGraphics) {
        val headerText = Component.translatable("dreamdisplays.ui.title")
        guiGraphics.drawString(
            font,
            headerText,
            (width - font.width(headerText)) / 2,
            15,
            0xFFFFFFFF.toInt(),
            true
        )
    }

    private fun updateWidgetStates(displayScreen: DisplayScreen) {
        syncReset?.active = displayScreen.owner && displayScreen.isSync
        renderDReset?.active = displayScreen.renderDistance != Initializer.config.defaultDistance
        qualityReset?.active = displayScreen.quality != "720"
        brightnessReset?.active = abs((brightness?.value ?: 0.5) - 0.5) > 0.01
        volumeReset?.active = abs((volume?.value ?: 0.5) - 0.5) > 0.01
        sync?.active = displayScreen.owner
        deleteButton?.active = displayScreen.owner
    }

    private fun renderErrorState(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        disableAllWidgets()

        val errorComponents = TooltipFactory.errorMessages()
        var yP = height / 2 - ((font.lineHeight + 2) * errorComponents.size) / 2

        for (component in errorComponents) {
            guiGraphics.drawString(
                font,
                component,
                width / 2 - font.width(component) / 2,
                yP,
                0xFFFFFFFF.toInt(),
                true
            )
            yP += font.lineHeight + 2
        }

        deleteButton?.render(guiGraphics, mouseX, mouseY, delta)
        reportButton?.render(guiGraphics, mouseX, mouseY, delta)
    }

    private fun disableAllWidgets() {
        volume?.active = false
        renderD?.active = false
        quality?.active = false
        sync?.active = false
        backButton?.active = false
        forwardButton?.active = false
        pauseButton?.active = false
        renderDReset?.active = false
        qualityReset?.active = false
        volumeReset?.active = false
        syncReset?.active = false
    }

    private fun renderSettingRows(
        guiGraphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        displayScreen: DisplayScreen,
        maxSW: Int,
        startY: Int,
    ) {
        var cY = startY

        cY = renderSettingRow(
            guiGraphics, mouseX, mouseY, maxSW, cY, volume, volumeReset,
            "dreamdisplays.button.volume", TooltipFactory.volumeTooltip(volume?.value ?: 0.5)
        )

        cY = renderSettingRow(
            guiGraphics, mouseX, mouseY, maxSW, cY, renderD, renderDReset,
            "dreamdisplays.button.render-distance", TooltipFactory.renderDistanceTooltip(renderD?.value ?: 0.5)
        )

        val qualityList = displayScreen.getQualityList()
        val currentQuality = toQuality(((quality?.value ?: 0.0) * qualityList.size).toInt())
        val showHighQualityWarning = (displayScreen.quality.toIntOrNull() ?: 0) >= 1080
        cY = renderSettingRow(
            guiGraphics, mouseX, mouseY, maxSW, cY, quality, qualityReset,
            "dreamdisplays.button.quality", TooltipFactory.qualityTooltip(currentQuality, showHighQualityWarning)
        )

        cY = renderSettingRow(
            guiGraphics, mouseX, mouseY, maxSW, cY, brightness, brightnessReset,
            "dreamdisplays.button.brightness", TooltipFactory.brightnessTooltip(brightness?.value ?: 0.5)
        )

        cY += 10
        cY = renderSettingRow(
            guiGraphics, mouseX, mouseY, maxSW, cY, sync, syncReset,
            "dreamdisplays.button.synchronization", TooltipFactory.syncTooltip(sync?.value ?: false)
        )
    }

    private fun renderSettingRow(
        guiGraphics: GuiGraphics,
        mouseX: Int,
        mouseY: Int,
        maxSW: Int,
        cY: Int,
        widget: net.minecraft.client.gui.components.AbstractWidget?,
        resetButton: Button?,
        labelKey: String,
        tooltip: List<Component>,
    ): Int {
        if (widget != null && resetButton != null) {
            widget.x = width / 2 + maxSW / 2 - 80 - WIDGET_HEIGHT - 5
            widget.y = cY
            widget.width = 80
            widget.height = WIDGET_HEIGHT

            resetButton.x = width / 2 + maxSW / 2 - WIDGET_HEIGHT
            resetButton.y = cY
            resetButton.width = WIDGET_HEIGHT
            resetButton.height = WIDGET_HEIGHT
        }

        val label = Component.translatable(labelKey)
        val labelX = width / 2 - maxSW / 2
        val labelY = cY + WIDGET_HEIGHT / 2 - font.lineHeight / 2
        guiGraphics.drawString(font, label, labelX, labelY, 0xFFFFFFFF.toInt(), true)

        renderTooltipIfHovered(guiGraphics, mouseX, mouseY, labelX, labelY, font.width(label), font.lineHeight, tooltip)

        return cY + WIDGET_HEIGHT + 5
    }

    private fun renderActionButtonTooltips(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        deleteButton?.let {
            renderTooltipIfHovered(
                guiGraphics,
                mouseX,
                mouseY,
                it.x,
                it.y,
                it.width,
                it.height,
                TooltipFactory.deleteTooltip()
            )
        }
        reportButton?.let {
            renderTooltipIfHovered(
                guiGraphics,
                mouseX,
                mouseY,
                it.x,
                it.y,
                it.width,
                it.height,
                TooltipFactory.reportTooltip()
            )
        }
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
        if (mouseX in elementX..elementX + elementWidth && mouseY in elementY..elementY + elementHeight) {
            guiGraphics.setComponentTooltipForNextFrame(font, tooltip, mouseX, mouseY)
        }
    }

    private fun toQuality(resolution: Int): String {
        val list = screen?.getQualityList() ?: emptyList()
        if (list.isEmpty()) return "144"
        val i = max(min(resolution, list.size - 1), 0)
        return list[i].toString()
    }

    private fun fromQuality(quality: String): Int {
        val displayScreen = screen ?: return 0
        val list = displayScreen.getQualityList()
        if (list.isEmpty()) return 0

        val cQ = quality.replace("p", "").toIntOrNull() ?: return 0

        var closest = list.first()
        var minDiff = abs(cQ - closest)
        for (q in list) {
            val diff = abs(q - cQ)
            if (diff < minDiff) {
                minDiff = diff
                closest = q
            }
        }

        return list.indexOf(closest)
    }
}

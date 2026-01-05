package com.dreamdisplays.screen.configuration

import com.dreamdisplays.Initializer
import com.dreamdisplays.screen.Manager
import com.dreamdisplays.screen.widgets.Button
import com.dreamdisplays.screen.widgets.Slider
import com.dreamdisplays.screen.widgets.Toggle
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import com.dreamdisplays.screen.Screen as DisplayScreen
import kotlin.math.floor

/**
 * Factory for creating widgets used in the configuration screen.
 */
object WidgetFactory {

    fun createVolumeSlider(screen: DisplayScreen): Slider {
        return object : Slider(
            0, 0, 0, 0,
            Component.literal("${floor(screen.volume * 200).toInt()}%"),
            screen.volume
        ) {
            override fun updateMessage() {
                message = Component.literal("${floor(value * 200).toInt()}%")
            }

            override fun applyValue() {
                screen.volume = value
            }
        }
    }

    fun createVolumeResetButton(screen: DisplayScreen, volumeSlider: () -> Slider?): Button {
        return object : Button(
            0, 0, 0, 0, 64, 64,
            Identifier.fromNamespaceAndPath(Initializer.MOD_ID, "bri"), 2
        ) {
            override fun onPress() {
                screen.volume = 0.5
                volumeSlider()?.let {
                    it.value = 0.5
                    it.message = Component.literal("100%")
                }
            }
        }
    }

    fun createBackButton(screen: DisplayScreen): Button {
        return object : Button(
            0, 0, 0, 0, 64, 64,
            Identifier.fromNamespaceAndPath(Initializer.MOD_ID, "bbi"), 2
        ) {
            override fun onPress() {
                screen.seekBackward()
            }
        }
    }

    fun createForwardButton(screen: DisplayScreen): Button {
        return object : Button(
            0, 0, 0, 0, 64, 64,
            Identifier.fromNamespaceAndPath(Initializer.MOD_ID, "bfi"), 2
        ) {
            override fun onPress() {
                screen.seekForward()
            }
        }
    }

    fun createPauseButton(screen: DisplayScreen): Button {
        val button = object : Button(
            0, 0, 0, 0, 64, 64,
            Identifier.fromNamespaceAndPath(Initializer.MOD_ID, "bpi"), 2
        ) {
            override fun onPress() {
                screen.setPaused(!screen.getPaused())
                setIconTextureId(
                    if (screen.getPaused()) Identifier.fromNamespaceAndPath(Initializer.MOD_ID, "bupi")
                    else Identifier.fromNamespaceAndPath(Initializer.MOD_ID, "bpi")
                )
            }
        }
        button.setIconTextureId(
            if (screen.getPaused()) Identifier.fromNamespaceAndPath(Initializer.MOD_ID, "bupi")
            else Identifier.fromNamespaceAndPath(Initializer.MOD_ID, "bpi")
        )
        return button
    }

    fun createRenderDistanceSlider(screen: DisplayScreen): Slider {
        return object : Slider(
            0, 0, 0, 0,
            Component.nullToEmpty(screen.renderDistance.toString()),
            (screen.renderDistance - 24) / (128.0 - 24.0)
        ) {
            override fun updateMessage() {
                message = Component.nullToEmpty(((value * (128 - 24) + 24).toInt()).toString())
            }

            override fun applyValue() {
                val newDistance = (value * (128 - 24) + 24).toInt()
                screen.renderDistance = newDistance
                Manager.saveScreenData(screen)
            }
        }
    }

    fun createRenderDistanceResetButton(screen: DisplayScreen, renderDSlider: () -> Slider?): Button {
        return object : Button(
            0, 0, 0, 0, 64, 64,
            Identifier.fromNamespaceAndPath(Initializer.MOD_ID, "bri"), 2
        ) {
            override fun onPress() {
                screen.renderDistance = Initializer.config.defaultDistance
                renderDSlider()?.let {
                    it.value = (Initializer.config.defaultDistance - 24) / (128.0 - 24.0)
                    it.message = Component.nullToEmpty(Initializer.config.defaultDistance.toString())
                }
                Manager.saveScreenData(screen)
            }
        }
    }

    fun createQualitySlider(screen: DisplayScreen, toQuality: (Int) -> String, fromQuality: (String) -> Int): Slider {
        val qualityList = screen.getQualityList()
        return object : Slider(
            0, 0, 0, 0,
            Component.nullToEmpty("${screen.quality}p"),
            fromQuality(screen.quality).toDouble() / qualityList.size.coerceAtLeast(1)
        ) {
            override fun updateMessage() {
                val currentList = screen.getQualityList()
                message = Component.nullToEmpty("${toQuality((value * currentList.size).toInt())}p")
            }

            override fun applyValue() {
                val currentList = screen.getQualityList()
                screen.quality = toQuality((value * currentList.size).toInt())
            }
        }
    }

    fun createQualityResetButton(screen: DisplayScreen, qualitySlider: () -> Slider?, toQuality: (Int) -> String, fromQuality: (String) -> Int): Button {
        return object : Button(
            0, 0, 0, 0, 64, 64,
            Identifier.fromNamespaceAndPath(Initializer.MOD_ID, "bri"), 2
        ) {
            override fun onPress() {
                val targetIndex = fromQuality("720")
                screen.quality = toQuality(targetIndex).replace("p", "")
                qualitySlider()?.let {
                    it.value = targetIndex.toDouble() / screen.getQualityList().size.coerceAtLeast(1)
                    it.message = Component.nullToEmpty("${toQuality(targetIndex)}p")
                }
            }
        }
    }

    fun createBrightnessSlider(screen: DisplayScreen): Slider {
        return object : Slider(
            0, 0, 0, 0,
            Component.literal("${floor(screen.brightness * 100).toInt()}%"),
            screen.brightness / 2.0
        ) {
            override fun updateMessage() {
                message = Component.literal("${floor(value * 200).toInt()}%")
            }

            override fun applyValue() {
                screen.brightness = (value * 2.0).toFloat()
            }
        }
    }

    fun createBrightnessResetButton(screen: DisplayScreen, brightnessSlider: () -> Slider?): Button {
        return object : Button(
            0, 0, 0, 0, 64, 64,
            Identifier.fromNamespaceAndPath(Initializer.MOD_ID, "bri"), 2
        ) {
            override fun onPress() {
                screen.brightness = 1.0f
                brightnessSlider()?.let {
                    it.value = 0.5
                    it.message = Component.literal("100%")
                }
            }
        }
    }

    fun createSyncToggle(screen: DisplayScreen, syncResetGetter: () -> Button?): Toggle {
        return object : Toggle(
            0, 0, 0, 0,
            Component.translatable(if (screen.isSync) "dreamdisplays.button.enabled" else "dreamdisplays.button.disabled"),
            screen.isSync
        ) {
            override fun updateMessage() {
                message = Component.translatable(if (value) "dreamdisplays.button.enabled" else "dreamdisplays.button.disabled")
            }

            override fun applyValue() {
                val syncReset = syncResetGetter()
                if (screen.owner && syncReset != null) {
                    screen.isSync = value
                    syncReset.active = !value
                    screen.waitForMFInit { screen.sendSync() }
                }
            }
        }
    }

    fun createSyncResetButton(screen: DisplayScreen, syncToggle: () -> Toggle?): Button {
        return object : Button(
            0, 0, 0, 0, 64, 64,
            Identifier.fromNamespaceAndPath(Initializer.MOD_ID, "bri"), 2
        ) {
            override fun onPress() {
                if (screen.owner) {
                    syncToggle()?.setValue(false)
                    screen.waitForMFInit { screen.sendSync() }
                }
            }
        }
    }
}

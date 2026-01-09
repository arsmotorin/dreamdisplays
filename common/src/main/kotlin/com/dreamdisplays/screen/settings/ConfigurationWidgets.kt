package com.dreamdisplays.screen.settings

import com.dreamdisplays.ModInitializer
import com.dreamdisplays.net.c2s.ReportPacket
import com.dreamdisplays.net.common.DeletePacket
import com.dreamdisplays.screen.DisplayScreen
import com.dreamdisplays.screen.managers.ScreenManager
import com.dreamdisplays.screen.managers.SettingsManager
import com.dreamdisplays.screen.settings.util.QualityConverter
import com.dreamdisplays.screen.settings.widgets.Button
import com.dreamdisplays.screen.settings.widgets.Slider
import com.dreamdisplays.screen.settings.widgets.Timeline
import com.dreamdisplays.screen.settings.widgets.Toggle
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import org.jspecify.annotations.NullMarked
import java.util.function.LongSupplier
import kotlin.math.floor

/**
 * Factory for creating configuration screen widgets.
 */
@NullMarked
object ConfigurationWidgets {

    fun createVolumeSlider(screen: DisplayScreen): Slider {
        return object : Slider(
            0, 0, 0, 0,
            Component.literal(floor(screen.volume * 200).toInt().toString() + "%"),
            screen.volume
        ) {
            override fun updateMessage() {
                setMessage(Component.literal(floor(value * 200).toInt().toString() + "%"))
            }

            override fun applyValue() {
                screen.volume = value
            }
        }
    }

    fun createBackButton(screen: DisplayScreen): Button {
        return object : Button(
            0, 0, 0, 0, 64, 64,
            Identifier.fromNamespaceAndPath(ModInitializer.MOD_ID, "bbi"),
            2
        ) {
            override fun onPress() {
                screen.seekBackward()
            }
        }
    }

    fun createForwardButton(screen: DisplayScreen): Button {
        return object : Button(
            0, 0, 0, 0, 64, 64,
            Identifier.fromNamespaceAndPath(ModInitializer.MOD_ID, "bfi"),
            2
        ) {
            override fun onPress() {
                screen.seekForward()
            }
        }
    }

    fun createPauseButton(screen: DisplayScreen): Button {
        val button = object : Button(
            0, 0, 0, 0, 64, 64,
            Identifier.fromNamespaceAndPath(ModInitializer.MOD_ID, "bpi"),
            2
        ) {
            override fun onPress() {
                screen.setPaused(!screen.getPaused())
                setIconTextureId(
                    if (screen.getPaused())
                        Identifier.fromNamespaceAndPath(ModInitializer.MOD_ID, "bupi")
                    else
                        Identifier.fromNamespaceAndPath(ModInitializer.MOD_ID, "bpi")
                )
            }
        }

        button.setIconTextureId(
            if (screen.getPaused())
                Identifier.fromNamespaceAndPath(ModInitializer.MOD_ID, "bupi")
            else
                Identifier.fromNamespaceAndPath(ModInitializer.MOD_ID, "bpi")
        )

        return button
    }

    fun createTimeline(screen: DisplayScreen): Timeline {
        return object : Timeline(
            0, 0, 0, 0,
            LongSupplier { screen.getCurrentTimeNanos() },
            LongSupplier { screen.getDurationNanos() }
        ) {
            override fun onSeek(nanos: Long) {
                screen.seekVideoTo(nanos)
                if (screen.owner && screen.isSync) screen.sendSync()
            }
        }
    }

    fun createRenderDistanceSlider(screen: DisplayScreen, onApply: () -> Unit): Slider {
        return object : Slider(
            0, 0, 0, 0,
            Component.nullToEmpty(screen.renderDistance.toString()),
            (screen.renderDistance - 24) / (128 - 24).toDouble()
        ) {
            override fun updateMessage() {
                setMessage(Component.nullToEmpty(((value * (128 - 24)).toInt() + 24).toString()))
            }

            override fun applyValue() {
                val newDistance = (value * (128 - 24) + 24).toInt()
                screen.renderDistance = newDistance
                onApply()
            }
        }
    }

    fun createQualitySlider(screen: DisplayScreen): Slider {
        return object : Slider(
            0, 0, 0, 0,
            Component.nullToEmpty(screen.quality + "p"),
            QualityConverter.fromQuality(screen.quality, screen).toDouble() / screen.getQualityList().size
        ) {
            override fun updateMessage() {
                setMessage(
                    Component.nullToEmpty(
                        QualityConverter.toQuality((value * screen.getQualityList().size).toInt(), screen) + "p"
                    )
                )
            }

            override fun applyValue() {
                screen.quality = QualityConverter.toQuality((value * screen.getQualityList().size).toInt(), screen)
            }
        }
    }

    fun createBrightnessSlider(screen: DisplayScreen): Slider {
        return object : Slider(
            0, 0, 0, 0,
            Component.literal(floor((screen.brightness * 100).toDouble()).toInt().toString() + "%"),
            screen.brightness / 2.0
        ) {
            override fun updateMessage() {
                setMessage(Component.literal(floor(value * 200).toInt().toString() + "%"))
            }

            override fun applyValue() {
                screen.brightness = (value * 2.0).toFloat()
            }
        }
    }

    fun createRenderDistanceResetButton(screen: DisplayScreen, slider: Slider, onApply: () -> Unit): Button {
        return object : Button(
            0, 0, 0, 0, 64, 64,
            Identifier.fromNamespaceAndPath(ModInitializer.MOD_ID, "bri"),
            2
        ) {
            override fun onPress() {
                screen.renderDistance = ModInitializer.config.defaultDistance
                slider.value = (ModInitializer.config.defaultDistance - 24) / (128 - 24).toDouble()
                slider.setMessage(Component.nullToEmpty(ModInitializer.config.defaultDistance.toString()))
                onApply()
            }
        }
    }

    fun createQualityResetButton(screen: DisplayScreen, slider: Slider): Button {
        return object : Button(
            0, 0, 0, 0, 64, 64,
            Identifier.fromNamespaceAndPath(ModInitializer.MOD_ID, "bri"),
            2
        ) {
            override fun onPress() {
                val targetIndex = QualityConverter.fromQuality("720", screen)
                screen.quality = QualityConverter.toQuality(targetIndex, screen).replace("p", "")
                slider.value = targetIndex.toDouble() / screen.getQualityList().size
                slider.setMessage(Component.nullToEmpty(QualityConverter.toQuality(targetIndex, screen) + "p"))
            }
        }
    }

    fun createBrightnessResetButton(screen: DisplayScreen, slider: Slider?): Button {
        return object : Button(
            0, 0, 0, 0, 64, 64,
            Identifier.fromNamespaceAndPath(ModInitializer.MOD_ID, "bri"),
            2
        ) {
            override fun onPress() {
                screen.brightness = 1.0f
                slider?.let {
                    it.value = 0.5
                    it.setMessage(Component.literal("100%"))
                }
            }
        }
    }

    fun createVolumeResetButton(screen: DisplayScreen, slider: Slider?): Button {
        return object : Button(
            0, 0, 0, 0, 64, 64,
            Identifier.fromNamespaceAndPath(ModInitializer.MOD_ID, "bri"),
            2
        ) {
            override fun onPress() {
                screen.volume = 0.5
                slider?.let {
                    it.value = 0.5
                    it.setMessage(Component.literal("100%"))
                }
            }
        }
    }

    fun createSyncToggle(screen: DisplayScreen, syncResetButton: Button?): Toggle {
        return object : Toggle(
            0, 0, 0, 0,
            Component.translatable(
                if (screen.isSync) "dreamdisplays.button.enabled"
                else "dreamdisplays.button.disabled"
            ),
            screen.isSync
        ) {
            var resetButton: Button? = syncResetButton

            override fun updateMessage() {
                setMessage(
                    Component.translatable(
                        if (value) "dreamdisplays.button.enabled"
                        else "dreamdisplays.button.disabled"
                    )
                )
            }

            override fun applyValue() {
                if (screen.owner) {
                    screen.isSync = value
                    resetButton?.let { it.active = !value }
                    screen.waitForMFInit { screen.sendSync() }
                }
            }
        }
    }

    fun createSyncResetButton(screen: DisplayScreen, syncToggle: Toggle): Button {
        return object : Button(
            0, 0, 0, 0, 64, 64,
            Identifier.fromNamespaceAndPath(ModInitializer.MOD_ID, "bri"),
            2
        ) {
            override fun onPress() {
                if (screen.owner) {
                    syncToggle.setToggleValue(false)
                    screen.waitForMFInit { screen.sendSync() }
                }
            }
        }
    }

    fun createDeleteButton(screen: DisplayScreen, onClose: () -> Unit): Button {
        return object : Button(
            0, 0, 0, 0, 64, 64,
            Identifier.fromNamespaceAndPath(ModInitializer.MOD_ID, "delete"),
            2
        ) {
            override fun onPress() {
                SettingsManager.removeDisplay(screen.uuid)
                ScreenManager.unregisterScreen(screen)
                ModInitializer.sendPacket(DeletePacket(screen.uuid))
                onClose()
            }
        }
    }

    fun createReportButton(screen: DisplayScreen, onClose: () -> Unit): Button {
        return object : Button(
            0, 0, 0, 0, 64, 64,
            Identifier.fromNamespaceAndPath(ModInitializer.MOD_ID, "report"),
            2
        ) {
            override fun onPress() {
                ModInitializer.sendPacket(ReportPacket(screen.uuid))
                onClose()
            }
        }
    }
}

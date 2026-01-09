package com.dreamdisplays.displays.settings.managers

import com.dreamdisplays.ModInitializer
import com.dreamdisplays.displays.DisplayScreen
import com.dreamdisplays.displays.managers.DisplayManager
import com.dreamdisplays.displays.managers.SettingsManager
import com.dreamdisplays.displays.settings.utils.QualityConverter
import com.dreamdisplays.displays.settings.widgets.ButtonWidget
import com.dreamdisplays.displays.settings.widgets.SliderWidget
import com.dreamdisplays.displays.settings.widgets.TimelineWidget
import com.dreamdisplays.displays.settings.widgets.ToggleWidget
import com.dreamdisplays.net.c2s.ReportPacket
import com.dreamdisplays.net.common.DeletePacket
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import org.jspecify.annotations.NullMarked
import java.util.function.LongSupplier
import kotlin.math.floor

/**
 * Factory for creating configuration screen widgets.
 */
@NullMarked
object WidgetManager {

    fun createVolumeSlider(display: DisplayScreen): SliderWidget {
        return object : SliderWidget(
            0, 0, 0, 0,
            Component.literal(floor(display.volume * 200).toInt().toString() + "%"),
            display.volume
        ) {
            override fun updateMessage() {
                setMessage(Component.literal(floor(value * 200).toInt().toString() + "%"))
            }

            override fun applyValue() {
                display.volume = value
            }
        }
    }

    fun createBackButton(display: DisplayScreen): ButtonWidget {
        return object : ButtonWidget(
            0, 0, 0, 0, 64, 64,
            Identifier.fromNamespaceAndPath(ModInitializer.MOD_ID, "bbi"),
            2
        ) {
            override fun onPress() {
                display.seekBackward()
            }
        }
    }

    fun createForwardButton(display: DisplayScreen): ButtonWidget {
        return object : ButtonWidget(
            0, 0, 0, 0, 64, 64,
            Identifier.fromNamespaceAndPath(ModInitializer.MOD_ID, "bfi"),
            2
        ) {
            override fun onPress() {
                display.seekForward()
            }
        }
    }

    fun createPauseButton(display: DisplayScreen): ButtonWidget {
        val button = object : ButtonWidget(
            0, 0, 0, 0, 64, 64,
            Identifier.fromNamespaceAndPath(ModInitializer.MOD_ID, "bpi"),
            2
        ) {
            override fun onPress() {
                display.setPaused(!display.getPaused())
                setIconTextureId(
                    if (display.getPaused())
                        Identifier.fromNamespaceAndPath(ModInitializer.MOD_ID, "bupi")
                    else
                        Identifier.fromNamespaceAndPath(ModInitializer.MOD_ID, "bpi")
                )
            }
        }

        button.setIconTextureId(
            if (display.getPaused())
                Identifier.fromNamespaceAndPath(ModInitializer.MOD_ID, "bupi")
            else
                Identifier.fromNamespaceAndPath(ModInitializer.MOD_ID, "bpi")
        )

        return button
    }

    fun createTimeline(display: DisplayScreen): TimelineWidget {
        return object : TimelineWidget(
            0, 0, 0, 0,
            LongSupplier { display.getCurrentTimeNanos() },
            LongSupplier { display.getDurationNanos() }
        ) {
            override fun onSeek(nanos: Long) {
                display.seekVideoTo(nanos)
                if (display.owner && display.isSync) display.sendSync()
            }
        }
    }

    fun createRenderDistanceSlider(display: DisplayScreen, onApply: () -> Unit): SliderWidget {
        return object : SliderWidget(
            0, 0, 0, 0,
            Component.nullToEmpty(display.renderDistance.toString()),
            (display.renderDistance - 24) / (128 - 24).toDouble()
        ) {
            override fun updateMessage() {
                setMessage(Component.nullToEmpty(((value * (128 - 24)).toInt() + 24).toString()))
            }

            override fun applyValue() {
                val newDistance = (value * (128 - 24) + 24).toInt()
                display.renderDistance = newDistance
                onApply()
            }
        }
    }

    fun createQualitySlider(display: DisplayScreen): SliderWidget {
        return object : SliderWidget(
            0, 0, 0, 0,
            Component.nullToEmpty(display.quality + "p"),
            QualityConverter.fromQuality(display.quality, display).toDouble() / display.getQualityList().size
        ) {
            override fun updateMessage() {
                setMessage(
                    Component.nullToEmpty(
                        QualityConverter.toQuality((value * display.getQualityList().size).toInt(), display) + "p"
                    )
                )
            }

            override fun applyValue() {
                display.quality = QualityConverter.toQuality((value * display.getQualityList().size).toInt(), display)
            }
        }
    }

    fun createBrightnessSlider(display: DisplayScreen): SliderWidget {
        return object : SliderWidget(
            0, 0, 0, 0,
            Component.literal(floor((display.brightness * 100).toDouble()).toInt().toString() + "%"),
            display.brightness / 2.0
        ) {
            override fun updateMessage() {
                setMessage(Component.literal(floor(value * 200).toInt().toString() + "%"))
            }

            override fun applyValue() {
                display.brightness = (value * 2.0).toFloat()
            }
        }
    }

    fun createRenderDistanceResetButton(display: DisplayScreen, slider: SliderWidget, onApply: () -> Unit): ButtonWidget {
        return object : ButtonWidget(
            0, 0, 0, 0, 64, 64,
            Identifier.fromNamespaceAndPath(ModInitializer.MOD_ID, "bri"),
            2
        ) {
            override fun onPress() {
                display.renderDistance = ModInitializer.config.defaultDistance
                slider.value = (ModInitializer.config.defaultDistance - 24) / (128 - 24).toDouble()
                slider.setMessage(Component.nullToEmpty(ModInitializer.config.defaultDistance.toString()))
                onApply()
            }
        }
    }

    fun createQualityResetButton(display: DisplayScreen, slider: SliderWidget): ButtonWidget {
        return object : ButtonWidget(
            0, 0, 0, 0, 64, 64,
            Identifier.fromNamespaceAndPath(ModInitializer.MOD_ID, "bri"),
            2
        ) {
            override fun onPress() {
                val targetIndex = QualityConverter.fromQuality("720", display)
                display.quality = QualityConverter.toQuality(targetIndex, display).replace("p", "")
                slider.value = targetIndex.toDouble() / display.getQualityList().size
                slider.setMessage(Component.nullToEmpty(QualityConverter.toQuality(targetIndex, display) + "p"))
            }
        }
    }

    fun createBrightnessResetButton(display: DisplayScreen, slider: SliderWidget?): ButtonWidget {
        return object : ButtonWidget(
            0, 0, 0, 0, 64, 64,
            Identifier.fromNamespaceAndPath(ModInitializer.MOD_ID, "bri"),
            2
        ) {
            override fun onPress() {
                display.brightness = 1.0f
                slider?.let {
                    it.value = 0.5
                    it.setMessage(Component.literal("100%"))
                }
            }
        }
    }

    fun createVolumeResetButton(display: DisplayScreen, slider: SliderWidget?): ButtonWidget {
        return object : ButtonWidget(
            0, 0, 0, 0, 64, 64,
            Identifier.fromNamespaceAndPath(ModInitializer.MOD_ID, "bri"),
            2
        ) {
            override fun onPress() {
                display.volume = 0.5
                slider?.let {
                    it.value = 0.5
                    it.setMessage(Component.literal("100%"))
                }
            }
        }
    }

    fun createSyncToggle(display: DisplayScreen, syncResetButton: ButtonWidget?): ToggleWidget {
        return object : ToggleWidget(
            0, 0, 0, 0,
            Component.translatable(
                if (display.isSync) "dreamdisplays.button.enabled"
                else "dreamdisplays.button.disabled"
            ),
            display.isSync
        ) {
            var resetButton: ButtonWidget? = syncResetButton

            override fun updateMessage() {
                setMessage(
                    Component.translatable(
                        if (value) "dreamdisplays.button.enabled"
                        else "dreamdisplays.button.disabled"
                    )
                )
            }

            override fun applyValue() {
                if (display.owner) {
                    display.isSync = value
                    resetButton?.let { it.active = !value }
                    display.waitForMFInit { display.sendSync() }
                }
            }
        }
    }

    fun createSyncResetButton(display: DisplayScreen, syncToggle: ToggleWidget): ButtonWidget {
        return object : ButtonWidget(
            0, 0, 0, 0, 64, 64,
            Identifier.fromNamespaceAndPath(ModInitializer.MOD_ID, "bri"),
            2
        ) {
            override fun onPress() {
                if (display.owner) {
                    syncToggle.setToggleValue(false)
                    display.waitForMFInit { display.sendSync() }
                }
            }
        }
    }

    fun createDeleteButton(display: DisplayScreen, onClose: () -> Unit): ButtonWidget {
        return object : ButtonWidget(
            0, 0, 0, 0, 64, 64,
            Identifier.fromNamespaceAndPath(ModInitializer.MOD_ID, "delete"),
            2
        ) {
            override fun onPress() {
                SettingsManager.removeDisplay(display.uuid)
                DisplayManager.unregisterDisplay(display)
                ModInitializer.sendPacket(DeletePacket(display.uuid))
                onClose()
            }
        }
    }

    fun createReportButton(display: DisplayScreen, onClose: () -> Unit): ButtonWidget {
        return object : ButtonWidget(
            0, 0, 0, 0, 64, 64,
            Identifier.fromNamespaceAndPath(ModInitializer.MOD_ID, "report"),
            2
        ) {
            override fun onPress() {
                ModInitializer.sendPacket(ReportPacket(display.uuid))
                onClose()
            }
        }
    }
}

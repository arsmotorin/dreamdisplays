package com.dreamdisplays.displays.mediaplayer.pipelines

import me.inotsleep.utils.logging.LoggingManager
import org.freedesktop.gstreamer.Bus
import org.freedesktop.gstreamer.Format
import org.freedesktop.gstreamer.Gst
import org.freedesktop.gstreamer.GstObject
import org.freedesktop.gstreamer.Pipeline
import org.freedesktop.gstreamer.State
import org.freedesktop.gstreamer.event.SeekFlags
import java.util.EnumSet

class AudioPipeline {
    companion object {
        fun build(uri: String, volume: Double, onEos: () -> Unit): Pipeline? {
            LoggingManager.info("[AudioPipelineBuilder] Building AUDIO pipeline for: $uri")
            val desc = "souphttpsrc location=\"" + uri + "\" ! decodebin ! audioconvert ! audioresample " +
                    "! volume name=volumeElement volume=1 ! audioamplify name=ampElement amplification=" + volume +
                    " ! autoaudiosink"
            LoggingManager.info("[AudioPipelineBuilder] Audio pipeline desc: $desc")

            val p = Gst.parseLaunch(desc) as Pipeline?
            if (p == null) {
                LoggingManager.error("[AudioPipelineBuilder] Gst.parseLaunch returned null for audio pipeline")
                return null
            }

            val bus = p.bus
            bus.connect(Bus.ERROR { src: GstObject?, _: Int, msg: String? -> LoggingManager.error("[AudioPipelineBuilder AUDIO ERROR] " + src!!.name + ": " + msg) })
            bus.connect(Bus.EOS { _: GstObject? ->
                LoggingManager.info("[AudioPipelineBuilder AUDIO] EOS - looping")
                onEos()
            })
            bus.connect { _: GstObject?, old: State?, cur: State?, _: State? -> LoggingManager.info("[AudioPipelineBuilder AUDIO] State: $old -> $cur") }

            LoggingManager.info("[AudioPipelineBuilder] Audio pipeline built")
            return p
        }

        fun applyVolume(pipeline: Pipeline?, volume: Double) {
            if (pipeline == null) return
            val v = pipeline.getElementByName("volumeElement")
            if (v != null) v.set("volume", 1)
            val a = pipeline.getElementByName("ampElement")
            if (a != null) a.set("amplification", volume)
        }

        fun seek(pipeline: Pipeline?, nanos: Long, flags: EnumSet<SeekFlags>) {
            pipeline?.seekSimple(Format.TIME, flags, nanos)
        }
    }
}
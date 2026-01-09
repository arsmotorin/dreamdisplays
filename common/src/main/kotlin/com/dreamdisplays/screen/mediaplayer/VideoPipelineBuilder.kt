package com.dreamdisplays.screen.mediaplayer

import org.freedesktop.gstreamer.Bus
import org.freedesktop.gstreamer.GstObject
import org.freedesktop.gstreamer.Pipeline
import org.freedesktop.gstreamer.State
import org.freedesktop.gstreamer.elements.AppSink
import me.inotsleep.utils.logging.LoggingManager

class VideoPipelineBuilder {
    companion object {
        fun build(uri: String, sinkCallback: (AppSink) -> Unit): Pipeline? {
            LoggingManager.info("[VideoPipelineBuilder] Building VIDEO pipeline for: $uri")
            val desc = java.lang.String.join(
                " ",
                "souphttpsrc location=\"$uri\"",
                "! typefind name=typefinder",
                "! decodebin ! videoconvert ! video/x-raw,format=RGBA ! appsink name=videosink sync=false"
            )
            LoggingManager.info("[VideoPipelineBuilder] Universal video pipeline desc: $desc")

            val p = org.freedesktop.gstreamer.Gst.parseLaunch(desc) as Pipeline?
            if (p == null) {
                LoggingManager.error("[VideoPipelineBuilder] Gst.parseLaunch returned null for universal video pipeline")
                return null
            }

            val sink = p.getElementByName("videosink") as AppSink?
            if (sink != null) {
                sinkCallback(sink)
            }
            p.pause()

            val bus = p.bus
            bus.connect(Bus.ERROR { src: GstObject?, _: Int, msg: String? -> LoggingManager.error("[VideoPipelineBuilder VIDEO ERROR] " + src!!.name + ": " + msg) })
            bus.connect(Bus.EOS { _: GstObject? -> LoggingManager.info("[VideoPipelineBuilder VIDEO] EOS") })
            bus.connect { _: GstObject?, old: State?, cur: State?, _: State? -> LoggingManager.info("[VideoPipelineBuilder VIDEO] State: $old -> $cur") }

            LoggingManager.info("[VideoPipelineBuilder] Universal video pipeline built and paused")
            return p
        }
    }
}

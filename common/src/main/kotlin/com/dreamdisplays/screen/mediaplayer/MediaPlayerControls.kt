package com.dreamdisplays.screen.mediaplayer

import com.dreamdisplays.util.Utils
import me.inotsleep.utils.logging.LoggingManager
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import org.freedesktop.gstreamer.Format
import java.util.EnumSet
import org.freedesktop.gstreamer.event.SeekFlags
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import java.util.function.ToIntFunction
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

object MediaPlayerControls {

    fun play(mp: MediaPlayer) {
        mp.safeExecute {
            val audioPos = mp.audioPipeline!!.queryPosition(Format.TIME)

            mp.audioPipeline!!.pause()
            if (mp.videoPipeline != null) mp.videoPipeline!!.pause()

            mp.audioPipeline!!.state
            if (mp.videoPipeline != null) mp.videoPipeline!!.state

            if (mp.videoPipeline != null && audioPos > 0) {
                mp.videoPipeline!!.seekSimple(
                    Format.TIME,
                    EnumSet.of<SeekFlags>(SeekFlags.FLUSH, SeekFlags.ACCURATE),
                    audioPos
                )
                mp.videoPipeline!!.state
            }

            val audioClock = mp.audioPipeline!!.clock
            if (audioClock != null && mp.videoPipeline != null) {
                mp.videoPipeline!!.clock = audioClock
                mp.videoPipeline!!.baseTime = mp.audioPipeline!!.baseTime
            }

            if (!mp.screen.getPaused()) {
                mp.audioPipeline!!.play()
                if (mp.videoPipeline != null) mp.videoPipeline!!.play()
            }
        }
    }

    fun pause(mp: MediaPlayer) {
        mp.safeExecute {
            if (!mp.isInitialized) return@safeExecute
            if (mp.videoPipeline != null) mp.videoPipeline!!.pause()
            if (mp.audioPipeline != null) mp.audioPipeline!!.pause()
        }
    }

    fun seekTo(mp: MediaPlayer, nanos: Long, b: Boolean) {
        mp.safeExecute {
            if (!mp.isInitialized) return@safeExecute
            val flags = EnumSet.of<SeekFlags>(SeekFlags.FLUSH, SeekFlags.ACCURATE)
            mp.audioPipeline!!.pause()
            if (mp.videoPipeline != null) mp.videoPipeline!!.pause()
            if (mp.videoPipeline != null) mp.videoPipeline!!.seekSimple(Format.TIME, flags, nanos)
            mp.audioPipeline!!.seekSimple(Format.TIME, flags, nanos)
            if (mp.videoPipeline != null) mp.videoPipeline!!.state
            mp.audioPipeline!!.play()
            if (mp.videoPipeline != null && !mp.screen.getPaused()) mp.videoPipeline!!.play()

            if (b) mp.screen.afterSeek()
        }
    }

    fun seekToFast(mp: MediaPlayer, nanos: Long) {
        mp.safeExecute {
            if (!mp.isInitialized) return@safeExecute
            val flags = EnumSet.of<SeekFlags>(SeekFlags.FLUSH, SeekFlags.KEY_UNIT)
            mp.audioPipeline!!.pause()
            if (mp.videoPipeline != null) mp.videoPipeline!!.pause()
            if (mp.videoPipeline != null) mp.videoPipeline!!.seekSimple(Format.TIME, flags, nanos)
            mp.audioPipeline!!.seekSimple(Format.TIME, flags, nanos)
            if (mp.videoPipeline != null) mp.videoPipeline!!.state
            mp.audioPipeline!!.play()
            if (mp.videoPipeline != null && !mp.screen.getPaused()) mp.videoPipeline!!.play()
        }
    }

    fun seekRelative(mp: MediaPlayer, s: Double) {
        mp.safeExecute {
            if (!mp.isInitialized) return@safeExecute
            val cur = mp.audioPipeline!!.queryPosition(Format.TIME)
            val tgt = max(0, cur + (s * 1e9).toLong())
            val dur = max(0, mp.audioPipeline!!.queryDuration(Format.TIME) - 1)
            val flags = EnumSet.of<SeekFlags>(SeekFlags.FLUSH, SeekFlags.ACCURATE)
            mp.audioPipeline!!.pause()
            if (mp.videoPipeline != null) mp.videoPipeline!!.pause()
            if (mp.videoPipeline != null) mp.videoPipeline!!.seekSimple(Format.TIME, flags, min(tgt, dur))
            mp.audioPipeline!!.seekSimple(Format.TIME, flags, min(tgt, dur))
            if (mp.videoPipeline != null) mp.videoPipeline!!.state
            mp.audioPipeline!!.play()
            if (mp.videoPipeline != null && !mp.screen.getPaused()) mp.videoPipeline!!.play()

            mp.screen.afterSeek()
        }
    }

    fun stop(mp: MediaPlayer) {
        if (mp.terminated.getAndSet(true)) return
        mp.safeExecute {
            GStreamerUtils.safeStopAndDispose(mp.videoPipeline)
            GStreamerUtils.safeStopAndDispose(mp.audioPipeline)
            mp.videoPipeline = null
            mp.audioPipeline = null
            mp.gstExecutor.shutdown()
            mp.frameExecutor.shutdown()
        }
    }

    fun setVolume(mp: MediaPlayer, volume: Double) {
        mp.userVolume = max(0.0, min(2.0, volume))
        mp.currentVolume = mp.userVolume * mp.lastAttenuation
        mp.safeExecute { applyVolume(mp) }
    }

    fun setBrightness(mp: MediaPlayer, brightness: Double) {
        mp.brightness = max(0.0, min(2.0, brightness))
    }

    fun setQuality(mp: MediaPlayer, quality: String) {
        mp.safeExecute { changeQuality(mp, quality) }
    }

    fun tick(mp: MediaPlayer, playerPos: BlockPos, maxRadius: Double) {
        if (!mp.isInitialized) return

        val dist = mp.screen.getDistanceToScreen(playerPos)
        val attenuation = (1.0 - min(1.0, dist / maxRadius)).pow(2.0)
        if (abs(attenuation - mp.lastAttenuation) > 1e-5) {
            mp.lastAttenuation = attenuation
            mp.currentVolume = mp.userVolume * attenuation
            LoggingManager.info("[MediaPlayer] Distance attenuation: ${mp.currentVolume}")
            mp.safeExecute { applyVolume(mp) }
        }
    }

    private fun applyVolume(mp: MediaPlayer) {
        AudioPipelineBuilder.applyVolume(mp.audioPipeline, mp.currentVolume)
    }

    private fun changeQuality(mp: MediaPlayer, desired: String) {
        if (!mp.isInitialized || mp.currentVideoUrl == null) return

        val target: Int
        try {
            target = desired.replace("\\D+".toRegex(), "").toInt()
        } catch (_: NumberFormatException) {
            return
        }

        try {
            val videoId = Utils.extractVideoId(mp.youtubeUrl)
            val cleanUrl = "https://www.youtube.com/watch?v=$videoId"

            val info = StreamInfo.getInfo(ServiceList.YouTube.getStreamExtractor(cleanUrl))
            val videoStreams = info.videoStreams

            // Check if exact match exists
            videoStreams.stream().anyMatch { vs: VideoStream? -> parseQuality(vs!!.getResolution()) == target }

            // Look for the best matching stream
            val best = videoStreams.stream()
                .min(Comparator.comparingInt<VideoStream>(ToIntFunction { vs: VideoStream -> abs(parseQuality(vs.getResolution()) - target) }))

            if (best.isEmpty || best.get().url == mp.currentVideoUrl) {
                mp.lastQuality = target
                return
            }

            Minecraft.getInstance().execute { mp.screen.reloadTexture() }

            val pos = mp.audioPipeline!!.queryPosition(Format.TIME)
            mp.audioPipeline!!.pause()

            GStreamerUtils.safeStopAndDispose(mp.videoPipeline)

            val newVid = VideoPipelineBuilder.build(best.get().url!!) { sink ->
                mp.bufferPreparator.configureVideoSink(mp, sink)
            }

            val clock = mp.audioPipeline!!.clock
            if (clock != null) {
                newVid?.clock = clock
                newVid?.baseTime = mp.audioPipeline!!.baseTime
            }
            newVid?.pause()
            newVid?.state

            val flags = EnumSet.of<SeekFlags>(SeekFlags.FLUSH, SeekFlags.ACCURATE)
            mp.audioPipeline!!.seekSimple(Format.TIME, flags, pos)
            newVid?.seekSimple(Format.TIME, flags, pos)

            if (!mp.screen.getPaused()) {
                mp.audioPipeline!!.play()
                newVid?.play()
            }

            mp.videoPipeline = newVid
            mp.currentVideoUrl = best.get().url as java.lang.String?
            mp.lastQuality = parseQuality(best.get().getResolution())
        } catch (e: Exception) {
            LoggingManager.error("[MediaPlayer] Failed to change quality", e)
        }
    }

    fun parseQuality(resolution: String): Int {
        return try {
            resolution.replace("\\D+".toRegex(), "").toInt()
        } catch (_: Exception) {
            Int.MAX_VALUE
        }
    }
}

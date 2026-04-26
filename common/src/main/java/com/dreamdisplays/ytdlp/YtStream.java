package com.dreamdisplays.ytdlp;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class YtStream {

    private final String url;
    private final String mimeType;
    private final @Nullable String container;
    private final @Nullable String protocol;
    private final @Nullable String resolution;
    private final @Nullable String audioTrackId;
    private final @Nullable String audioTrackName;
    private final @Nullable String vcodec;
    private final @Nullable String acodec;
    private final @Nullable Double fps;
    private final @Nullable Double tbrKbps;
    private final boolean hasVideo;
    private final boolean hasAudio;
    private final boolean live;
    private final boolean seekable;
    private final long durationNanos;

    public YtStream(
            String url,
            String mimeType,
            @Nullable String container,
            @Nullable String protocol,
            @Nullable String resolution,
            @Nullable String audioTrackId,
            @Nullable String audioTrackName,
            @Nullable String vcodec,
            @Nullable String acodec,
            @Nullable Double fps,
            @Nullable Double tbrKbps,
            boolean hasVideo,
            boolean hasAudio,
            boolean live,
            boolean seekable,
            long durationNanos
    ) {
        this.url = url;
        this.mimeType = mimeType;
        this.container = container;
        this.protocol = protocol;
        this.resolution = resolution;
        this.audioTrackId = audioTrackId;
        this.audioTrackName = audioTrackName;
        this.vcodec = vcodec;
        this.acodec = acodec;
        this.fps = fps;
        this.tbrKbps = tbrKbps;
        this.hasVideo = hasVideo;
        this.hasAudio = hasAudio;
        this.live = live;
        this.seekable = seekable;
        this.durationNanos = durationNanos;
    }

    public String getUrl() {
        return url;
    }

    public String getMimeType() {
        return mimeType;
    }

    public @Nullable String getContainer() {
        return container;
    }

    public @Nullable String getProtocol() {
        return protocol;
    }

    public @Nullable String getResolution() {
        return resolution;
    }

    public @Nullable String getAudioTrackId() {
        return audioTrackId;
    }

    public @Nullable String getAudioTrackName() {
        return audioTrackName;
    }

    public @Nullable String getVcodec() {
        return vcodec;
    }

    public @Nullable String getAcodec() {
        return acodec;
    }

    public @Nullable Double getFps() {
        return fps;
    }

    public @Nullable Double getTbrKbps() {
        return tbrKbps;
    }

    public boolean hasVideo() {
        return hasVideo;
    }

    public boolean hasAudio() {
        return hasAudio;
    }

    public boolean isMuxed() {
        return hasVideo && hasAudio;
    }

    public boolean isLive() {
        return live;
    }

    public boolean isSeekable() {
        return seekable;
    }

    public long getDurationNanos() {
        return durationNanos;
    }

    @Override
    public String toString() {
        return "YtStream{" + mimeType
                + (container != null ? " container=" + container : "")
                + (protocol != null ? " proto=" + protocol : "")
                + (resolution != null ? " " + resolution : "")
                + (vcodec != null && !vcodec.equals("none") ? " v=" + vcodec : "")
                + (acodec != null && !acodec.equals("none") ? " a=" + acodec : "")
                + (fps != null ? " " + fps + "fps" : "")
                + (tbrKbps != null ? " " + tbrKbps + "kbps" : "")
                + (audioTrackId != null ? " lang=" + audioTrackId : "")
                + (live ? " live" : "")
                + (!seekable ? " nonseekable" : "")
                + "}";
    }
}

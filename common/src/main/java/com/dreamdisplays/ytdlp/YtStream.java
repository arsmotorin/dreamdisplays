package com.dreamdisplays.ytdlp;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class YtStream {

    private final String url;
    private final String mimeType;
    private final @Nullable String resolution;
    private final @Nullable String audioTrackId;
    private final @Nullable String audioTrackName;
    private final @Nullable String vcodec;
    private final @Nullable String acodec;
    private final @Nullable Double fps;
    private final @Nullable Double tbrKbps;

    public YtStream(
            String url,
            String mimeType,
            @Nullable String resolution,
            @Nullable String audioTrackId,
            @Nullable String audioTrackName,
            @Nullable String vcodec,
            @Nullable String acodec,
            @Nullable Double fps,
            @Nullable Double tbrKbps
    ) {
        this.url = url;
        this.mimeType = mimeType;
        this.resolution = resolution;
        this.audioTrackId = audioTrackId;
        this.audioTrackName = audioTrackName;
        this.vcodec = vcodec;
        this.acodec = acodec;
        this.fps = fps;
        this.tbrKbps = tbrKbps;
    }

    public String getUrl() {
        return url;
    }

    public String getMimeType() {
        return mimeType;
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

    @Override
    public String toString() {
        return "YtStream{" + mimeType
                + (resolution != null ? " " + resolution : "")
                + (vcodec != null && !vcodec.equals("none") ? " v=" + vcodec : "")
                + (acodec != null && !acodec.equals("none") ? " a=" + acodec : "")
                + (fps != null ? " " + fps + "fps" : "")
                + (tbrKbps != null ? " " + tbrKbps + "kbps" : "")
                + (audioTrackId != null ? " lang=" + audioTrackId : "")
                + "}";
    }
}

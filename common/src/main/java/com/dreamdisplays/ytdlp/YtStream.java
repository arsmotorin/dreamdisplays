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

    public YtStream(
            String url,
            String mimeType,
            @Nullable String resolution,
            @Nullable String audioTrackId,
            @Nullable String audioTrackName
    ) {
        this.url = url;
        this.mimeType = mimeType;
        this.resolution = resolution;
        this.audioTrackId = audioTrackId;
        this.audioTrackName = audioTrackName;
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
}

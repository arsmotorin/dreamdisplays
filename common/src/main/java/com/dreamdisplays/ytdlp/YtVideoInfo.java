package com.dreamdisplays.ytdlp;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

// TODO: remove
@NullMarked
public final class YtVideoInfo {

    private final String id;
    private final String title;
    private final @Nullable String uploader;
    private final @Nullable Long durationSec;
    private final @Nullable Long viewCount;
    private final @Nullable Long likeCount;
    private final @Nullable String publishedText;
    private final @Nullable Integer publishedDaysAgo;
    private final @Nullable String thumbnailUrl;

    public YtVideoInfo(
            String id,
            String title,
            @Nullable String uploader,
            @Nullable Long durationSec,
            @Nullable Long viewCount,
            @Nullable String thumbnailUrl
    ) {
        this(id, title, uploader, durationSec, viewCount, null, null, null, thumbnailUrl);
    }

    public YtVideoInfo(
            String id,
            String title,
            @Nullable String uploader,
            @Nullable Long durationSec,
            @Nullable Long viewCount,
            @Nullable Long likeCount,
            @Nullable String publishedText,
            @Nullable Integer publishedDaysAgo,
            @Nullable String thumbnailUrl
    ) {
        this.id = id;
        this.title = title;
        this.uploader = uploader;
        this.durationSec = durationSec;
        this.viewCount = viewCount;
        this.likeCount = likeCount;
        this.publishedText = publishedText;
        this.publishedDaysAgo = publishedDaysAgo;
        this.thumbnailUrl = thumbnailUrl;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public @Nullable String getUploader() {
        return uploader;
    }

    public @Nullable Long getDurationSec() {
        return durationSec;
    }

    public @Nullable Long getViewCount() {
        return viewCount;
    }

    public @Nullable Long getLikeCount() {
        return likeCount;
    }

    public @Nullable String getPublishedText() {
        return publishedText;
    }

    public @Nullable Integer getPublishedDaysAgo() {
        return publishedDaysAgo;
    }

    public boolean isRecent(int daysWindow) {
        return publishedDaysAgo != null && publishedDaysAgo >= 0 && publishedDaysAgo <= daysWindow;
    }

    public String getWatchUrl() {
        return "https://www.youtube.com/watch?v=" + id;
    }

    public String getThumbnailUrl() {
        return "https://i.ytimg.com/vi/" + id + "/mqdefault.jpg";
    }

    public String formatDuration() {
        if (durationSec == null || durationSec <= 0) return "";
        long s = durationSec;
        long h = s / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        if (h > 0) return String.format("%d:%02d:%02d", h, m, sec);
        return String.format("%d:%02d", m, sec);
    }

    public String formatViews() {
        if (viewCount == null || viewCount <= 0) return "";
        if (viewCount >= 1_000_000_000L)
            return String.format("%.1fB views", viewCount / 1_000_000_000.0);
        if (viewCount >= 1_000_000L)
            return String.format("%.1fM views", viewCount / 1_000_000.0);
        if (viewCount >= 1_000L)
            return String.format("%.1fK views", viewCount / 1_000.0);
        return viewCount + " views";
    }

    public String formatLikes() {
        if (likeCount == null || likeCount <= 0) return "";
        if (likeCount >= 1_000_000_000L)
            return String.format("%.1fB", likeCount / 1_000_000_000.0);
        if (likeCount >= 1_000_000L)
            return String.format("%.1fM", likeCount / 1_000_000.0);
        if (likeCount >= 1_000L)
            return String.format("%.1fK", likeCount / 1_000.0);
        return likeCount.toString();
    }
}

package com.dreamdisplays.ytdlp;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.ConcurrentHashMap;

@NullMarked
public final class VideoTitleCache {

    private static final ConcurrentHashMap<String, String> TITLES = new ConcurrentHashMap<>();

    public static void put(String videoId, String title) {
        if (videoId.isEmpty() || title.isEmpty()) return;
        TITLES.put(videoId, title);
    }

    public static @Nullable String get(String videoId) {
        return TITLES.get(videoId);
    }
}

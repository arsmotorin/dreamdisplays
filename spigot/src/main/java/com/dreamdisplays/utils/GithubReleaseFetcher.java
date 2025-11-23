package com.dreamdisplays.utils;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import me.inotsleep.utils.logging.LoggingManager;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

public class GithubReleaseFetcher {

    public record Release(@SerializedName("tag_name") String tagName, String name,
                          @SerializedName("html_url") String htmlUrl,
                          @SerializedName("published_at") String publishedAt) {

        @Override
            public String toString() {
                return String.format("Release[tag=%s, name=%s, publishedAt=%s, url=%s]",
                        tagName, name, publishedAt, htmlUrl);
            }
        }

    // Get releases from GitHub API
    public static List<Release> fetchReleases(String owner, String repo) throws Exception {
        HttpURLConnection conn = getHttpURLConnection(owner, repo);

        StringBuilder json = new StringBuilder();
        try (InputStream is = conn.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null) {
                json.append(line);
            }
        }

        Gson gson = new Gson();
        Type listType = new TypeToken<List<Release>>() {}.getType();
        return gson.fromJson(json.toString(), listType);
    }

    private static @NotNull HttpURLConnection getHttpURLConnection(String owner, String repo) throws IOException {
        String apiUrl = String.format("https://api.github.com/repos/%s/%s/releases", owner, repo);
        HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
        conn.setRequestProperty("User-Agent", "Java-GithubReleaseFetcher");

        if (conn.getResponseCode() != 200) {
            LoggingManager.error("GitHub API returned HTTP " + conn.getResponseCode());
        }
        return conn;
    }
}

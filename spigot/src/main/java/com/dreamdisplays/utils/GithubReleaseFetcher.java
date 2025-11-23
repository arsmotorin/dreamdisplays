package com.dreamdisplays.utils;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import me.inotsleep.utils.logging.LoggingManager;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

public class GithubReleaseFetcher {

    public record Release(
            @SerializedName("tag_name") String tagName,
            @SerializedName("name") String name,
            @SerializedName("html_url") String htmlUrl,
            @SerializedName("published_at") String publishedAt
    ) {}

    private static final Gson gson = new Gson();
    private static final HttpClient client = HttpClient.newHttpClient();

    public static List<Release> fetchReleases(String owner, String repo) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.github.com/repos/%s/%s/releases".formatted(owner, repo)))
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Updater")
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            LoggingManager.error("GitHub API error %d: %s".formatted(response.statusCode(), response.body()));
            return List.of();
        }

        return gson.fromJson(response.body(), new TypeToken<List<Release>>() {}.getType());
    }
}

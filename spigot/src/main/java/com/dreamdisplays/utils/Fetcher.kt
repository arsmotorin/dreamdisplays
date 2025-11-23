package com.dreamdisplays.utils

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import me.inotsleep.utils.logging.LoggingManager
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

object Fetcher {
    private val gson = Gson()
    private val client: HttpClient = HttpClient.newHttpClient()

    @Throws(Exception::class)
    fun fetchReleases(owner: String, repo: String): List<Release> {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.github.com/repos/$owner/$repo/releases"))
            .header("Accept", "application/vnd.github.v3+json")
            .header("User-Agent", "Updater")
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())

        if (response.statusCode() != 200) {
            LoggingManager.error("GitHub API error ${response.statusCode()}: ${response.body()}")
            return emptyList()
        }

        return gson.fromJson(
            response.body(),
            object : TypeToken<List<Release>>() {}.type
        ) ?: emptyList()
    }

    data class Release(
        @field:SerializedName("tag_name") val tagName: String,
        @field:SerializedName("name") val name: String,
        @field:SerializedName("html_url") val htmlUrl: String,
        @field:SerializedName("published_at") val publishedAt: String
    )
}

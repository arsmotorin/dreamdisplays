package com.dreamdisplays.utils

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.bukkit.Location
import org.bukkit.entity.Player
import org.jspecify.annotations.NullMarked
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.*

@NullMarked
object Reporter {
    private val client: HttpClient = HttpClient.newHttpClient()

    @Throws(IOException::class, InterruptedException::class)
    fun sendReport(
        loc: Location,
        displayVideoLink: String?,
        uuid: UUID,
        reporter: Player,
        webhookURL: String,
        ownerName: String?
    ) {
        val humanLoc = "${loc.world?.name} (x=${loc.blockX}, y=${loc.blockY}, z=${loc.blockZ})"

        // Create the embed JSON object
        val embed = JsonObject()
        embed.addProperty("description", "# 🛎️ New report")
        embed.addProperty("color", 0x2F3136)
        embed.addProperty(
            "timestamp", OffsetDateTime.now()
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        )

        val fields = JsonArray()
        fields.add(field("Location", humanLoc, false))
        fields.add(field("Video", displayVideoLink, false))
        fields.add(field("UUID", uuid.toString(), false))
        fields.add(field("Player", reporter.name, false))
        fields.add(field("Owner", ownerName, false))
        embed.add("fields", fields)

        val payload = JsonObject()

        val arr = JsonArray()
        arr.add(embed)

        payload.add("embeds", arr)

        val json = payload.toString()

        val req = HttpRequest.newBuilder()
            .uri(URI.create(webhookURL))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build()

        val resp = client.send(req, HttpResponse.BodyHandlers.ofString())
        if (resp.statusCode() / 100 != 2) {
            throw IOException("Discord webhook error ${resp.statusCode()}: ${resp.body()}")
        }
    }

    private fun field(name: String, value: String?, inline: Boolean): JsonObject {
        val f = JsonObject()
        f.addProperty("name", name)
        f.addProperty("value", value ?: "N/A")
        f.addProperty("inline", inline)
        return f
    }
}

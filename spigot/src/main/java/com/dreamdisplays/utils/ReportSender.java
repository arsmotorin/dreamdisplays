package com.dreamdisplays.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public class ReportSender {

    private static final HttpClient client = HttpClient.newHttpClient();

    public static void sendReport(Location loc, String displayVideoLink, UUID uuid, Player reporter, String webhookURL, String ownerName)
            throws IOException, InterruptedException {
        String humanLoc = String.format(
                "%s (x=%d, y=%d, z=%d)",
                loc.getWorld().getName(),
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()
        );

        // Create the embed JSON object
        JsonObject embed = new JsonObject();
        embed.addProperty("description", "# 🛎️ New report");
        embed.addProperty("color", 0x2F3136);
        embed.addProperty("timestamp", OffsetDateTime.now()
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));

        JsonArray fields = new JsonArray();
        fields.add(field("Location", humanLoc, false));
        fields.add(field("Video",  displayVideoLink, false));
        fields.add(field("UUID", uuid.toString(), false));
        fields.add(field("Player", reporter.getName(), false));
        fields.add(field("Owner", ownerName, false));
        embed.add("fields", fields);

        JsonObject payload = new JsonObject();

        JsonArray arr = new JsonArray();
        arr.add(embed);

        payload.add("embeds", arr);

        String json = payload.toString();

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(webhookURL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new IOException("Discord webhook error " + resp.statusCode() + ": " + resp.body());
        }
    }

    private static JsonObject field(String name, String value, boolean inline) {
        JsonObject f = new JsonObject();
        f.addProperty("name", name);
        f.addProperty("value", value);
        f.addProperty("inline", inline);
        return f;
    }
}
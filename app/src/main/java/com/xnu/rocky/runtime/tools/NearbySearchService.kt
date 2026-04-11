//
// OpenRocky — Voice-first AI Agent
// https://github.com/openrocky
//
// Developed by everettjf with the assistance of Claude Code and Codex.
// Date: 2026-03-25
// Copyright (c) 2026 everettjf. All rights reserved.
//

package com.xnu.rocky.runtime.tools

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class NearbySearchService(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun search(query: String, latitude: Double, longitude: Double): String = withContext(Dispatchers.IO) {
        try {
            // Use Nominatim (OpenStreetMap) for free POI search
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://nominatim.openstreetmap.org/search?q=$encoded&format=json&limit=10&lat=$latitude&lon=$longitude&addressdetails=1"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "OpenRocky/1.0")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext "No results"

            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            val results = json.parseToJsonElement(body).jsonArray

            if (results.isEmpty()) return@withContext "No places found for: $query"

            val places = results.take(10).mapIndexed { i, item ->
                val obj = item.jsonObject
                val name = obj["display_name"]?.jsonPrimitive?.contentOrNull ?: "Unknown"
                val type = obj["type"]?.jsonPrimitive?.contentOrNull ?: ""
                val lat = obj["lat"]?.jsonPrimitive?.contentOrNull ?: ""
                val lon = obj["lon"]?.jsonPrimitive?.contentOrNull ?: ""
                "${i + 1}. $name\n   Type: $type | Lat: $lat, Lon: $lon"
            }

            "Nearby places for \"$query\":\n\n${places.joinToString("\n\n")}"
        } catch (e: Exception) {
            "Nearby search error: ${e.message}"
        }
    }
}

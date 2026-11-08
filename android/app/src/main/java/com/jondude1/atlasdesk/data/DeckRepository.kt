package com.jondude1.atlasdesk.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Read-only client for deckd. GET is the only verb this app ever uses,
 * and /api/deck is the only path. There is no token in the app: deckd
 * holds the Home Assistant token server-side on the Pi.
 *
 * Endpoints, in order:
 *  1. tailnet  http://100.68.172.113:8098  (works from anywhere)
 *  2. home LAN http://192.168.4.23:8098    (verified answering 2026-11-08)
 */
class DeckRepository {

    data class Fetch(val deck: DeckState, val endpointLabel: String)

    private val endpoints = listOf(
        "Tailnet" to "http://100.68.172.113:8098",
        "LAN" to "http://192.168.4.23:8098",
    )

    suspend fun fetchDeck(): Fetch = withContext(Dispatchers.IO) {
        var lastError: Exception? = null
        for ((label, base) in endpoints) {
            try {
                val body = httpGet("$base/api/deck")
                return@withContext Fetch(DeckParser.parse(body), label)
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("no endpoints configured")
    }

    private fun httpGet(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 5_000
            conn.readTimeout = 8_000
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            check(code == 200) { "HTTP $code from $url" }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}

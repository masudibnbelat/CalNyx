// FILE: app/src/main/java/com/calnyx/app/network/ScreenStreamPrefs.kt
// NEW FILE - Screen Stream settings (2026-11-21).
// Saved in SharedPreferences, same pattern as the other CalNyx
// prefs files. Password is mandatory and at least 2 characters -
// the service and the relay server both enforce it.
package com.calnyx.app.network

import android.content.Context
import android.net.Uri

object ScreenStreamPrefs {
    private const val PREFS = "screenstream_prefs"
    private const val KEY_RELAY_URL = "relay_url"
    private const val KEY_PASSWORD = "password"
    private const val KEY_QUALITY = "quality"

    // Default relay URL - replace with your own Render URL after
    // deploying server.js (or change it in Settings in the app).
    const val DEFAULT_RELAY_URL = "https://calnyx-stream.onrender.com"

    fun savedRelayUrl(context: Context): String {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_RELAY_URL, DEFAULT_RELAY_URL) ?: DEFAULT_RELAY_URL
    }

    fun saveRelayUrl(context: Context, url: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_RELAY_URL, url.trim().trimEnd('/')).apply()
    }

    fun savedPassword(context: Context): String {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PASSWORD, "") ?: ""
    }

    fun savePassword(context: Context, password: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_PASSWORD, password).apply()
    }

    // "Low" / "Medium" / "High" - see ScreenStreamService for
    // what each one means (width, JPEG quality, frames/second).
    fun savedQuality(context: Context): String {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_QUALITY, "Medium") ?: "Medium"
    }

    fun saveQuality(context: Context, quality: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_QUALITY, quality).apply()
    }

    // Relay URL -> WebSocket URL: http(s)://host becomes
    // ws(s)://host/stream. The relay serves the viewer page on
    // the same host, so one URL is the whole setting.
    fun wsUrl(relayUrl: String): String {
        val base = relayUrl.trim().trimEnd('/')
        return when {
            base.startsWith("https://") -> "wss://" + base.removePrefix("https://") + "/stream"
            base.startsWith("http://") -> "ws://" + base.removePrefix("http://") + "/stream"
            else -> "wss://$base/stream"
        }
    }

    // Share link for a room: https://<relay>/?room=ABC123
    // The password is NOT in the link - the viewer types it on
    // the page (or the app passes it as a #pw= fragment, which
    // is never sent to the server).
    fun streamLink(relayUrl: String, roomCode: String): String {
        return relayUrl.trim().trimEnd('/') + "/?room=" + roomCode
    }

    // Pull the 6-letter room code out of a pasted link:
    //   https://host/?room=ABC123
    //   https://host/watch/ABC123
    //   ABC123
    // Returns null when there is no code to read.
    fun parseRoomCode(text: String): String? {
        val input = text.trim()
        if (input.isEmpty()) return null
        if (Regex("^[A-Za-z0-9]{6}$").matches(input)) return input.uppercase()
        return try {
            val uri = Uri.parse(input)
            val fromQuery = uri.getQueryParameter("room")?.trim()?.uppercase()
            if (fromQuery != null && Regex("^[A-Za-z0-9]{6}$").matches(fromQuery)) {
                fromQuery
            } else {
                val last = uri.lastPathSegment?.trim()?.uppercase()
                if (last != null && Regex("^[A-Za-z0-9]{6}$").matches(last)) last else null
            }
        } catch (_: Exception) {
            null
        }
    }

    // The host part of a pasted link, for the in-app viewer:
    // https://host/?room=ABC123 -> https://host
    // A bare room code has no host - the saved relay is used.
    fun parseBaseUrl(text: String, fallbackRelayUrl: String): String {
        val input = text.trim()
        return try {
            val uri = Uri.parse(input)
            val scheme = uri.scheme
            val host = uri.authority
            if (scheme != null && host != null) "$scheme://$host"
            else fallbackRelayUrl.trim().trimEnd('/')
        } catch (_: Exception) {
            fallbackRelayUrl.trim().trimEnd('/')
        }
    }
}

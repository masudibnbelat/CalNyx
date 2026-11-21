// FILE: app/src/main/java/com/calnyx/app/network/CameraLivePrefs.kt
// NEW FILE - Camera Live settings (2026-11-21). Task 5, Camera Live 1-to-1.
// Saved in SharedPreferences, same pattern as ScreenStreamPrefs.
// Mirrors the 02-stream-link-top ScreenStreamPrefs (3-arg
// streamLink with #pw=, parsePasswordFromLink) - the only
// differences are the Camera Live rules:
//   * password minimum 4 characters (screen keeps 2 - do not
//     copy this number into ScreenStreamPrefs)
//   * quality is only "480" or "720", default "480" (no 1080p)
//   * a front / back camera choice is saved here too
// The relay is the SAME Render relay as Screen Stream - one
// server, two modes (screen / camera). The relay URL is
// CODE-ONLY, never shown in the app UI.
package com.calnyx.app.thirdeye

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.net.Uri
import java.net.URLDecoder
import java.net.URLEncoder

object CameraLivePrefs {
    private const val PREFS = "cameralive_prefs"
    private const val KEY_RELAY_URL = "relay_url"
    private const val KEY_PASSWORD = "password"
    private const val KEY_QUALITY = "quality"
    private const val KEY_FACING = "facing"

    // Camera Live password: at least 4 characters. Enforced here
    // in the UI, in CameraLiveService, and on the relay.
    const val MIN_PASSWORD_LENGTH = 4

    // Relay URL - CODE ONLY, never shown in the app UI (2026-11-21).
    // Same relay as Screen Stream. Change this line only if the
    // relay moves to a different address.
    const val DEFAULT_RELAY_URL = "https://calnyx-screen-stream.onrender.com"

    fun savedRelayUrl(context: Context): String {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_RELAY_URL, DEFAULT_RELAY_URL) ?: DEFAULT_RELAY_URL
        // WRONG-HOST FIX (same as ScreenStreamPrefs): the first
        // Screen Stream build shipped "calnyx-stream" (missing
        // "screen") and that host answers 404. If that wrong host
        // is saved here, use the correct default.
        return if (saved.trim().trimEnd('/') == "https://calnyx-stream.onrender.com") {
            DEFAULT_RELAY_URL
        } else {
            saved
        }
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

    // "480" or "720" only. Anything else saved by an older build
    // falls back to "480".
    fun savedQuality(context: Context): String {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_QUALITY, "480") ?: "480"
        return if (saved == "720") "720" else "480"
    }

    fun saveQuality(context: Context, quality: String) {
        val value = if (quality == "720") "720" else "480"
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_QUALITY, value).apply()
    }

    // Front camera by default (a video call starts facing you).
    // Stored as a CameraCharacteristics.LENS_FACING_* value.
    fun savedFacing(context: Context): Int {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_FACING, CameraCharacteristics.LENS_FACING_FRONT)
    }

    fun saveFacing(context: Context, facing: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_FACING, facing).apply()
    }

    // WATCH SESSION (same idea as ScreenStreamPrefs): the room
    // being watched, so the watch survives leaving the app or
    // the Third Eye screen - when the screen opens again, the
    // viewer reopens itself, live, until "Stop watching".
    private const val KEY_WATCH_BASE = "watch_base"
    private const val KEY_WATCH_ROOM = "watch_room"
    private const val KEY_WATCH_PASSWORD = "watch_password"

    fun saveWatchSession(context: Context, baseUrl: String, roomCode: String, password: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_WATCH_BASE, baseUrl)
            .putString(KEY_WATCH_ROOM, roomCode)
            .putString(KEY_WATCH_PASSWORD, password)
            .apply()
    }

    // Triple(baseUrl, roomCode, password), or null when nothing
    // is being watched.
    fun loadWatchSession(context: Context): Triple<String, String, String>? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val room = prefs.getString(KEY_WATCH_ROOM, null) ?: return null
        val base = prefs.getString(KEY_WATCH_BASE, null) ?: return null
        val password = prefs.getString(KEY_WATCH_PASSWORD, null) ?: return null
        if (room.isBlank() || base.isBlank()) return null
        return Triple(base, room, password)
    }

    fun clearWatchSession(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_WATCH_BASE)
            .remove(KEY_WATCH_ROOM)
            .remove(KEY_WATCH_PASSWORD)
            .apply()
    }

    // Relay URL -> WebSocket URL: http(s)://host becomes
    // ws(s)://host/stream. Same relay as Screen Stream.
    fun wsUrl(relayUrl: String): String {
        val base = relayUrl.trim().trimEnd('/')
        return when {
            base.startsWith("https://") -> "wss://" + base.removePrefix("https://") + "/stream"
            base.startsWith("http://") -> "ws://" + base.removePrefix("http://") + "/stream"
            else -> "wss://$base/stream"
        }
    }

    // Share link for a room: https://<relay>/?room=ABC123#pw=<password>
    // The password IS in the link (Task 3, 2026-11-21), as a #pw=
    // fragment - a fragment is never sent to the server, the
    // browser / app reads it locally, so clicking the link opens
    // the stream at once with no separate password entry. Anyone
    // with the link can watch - that is the trade-off.
    fun streamLink(relayUrl: String, roomCode: String, password: String): String {
        val base = relayUrl.trim().trimEnd('/') + "/?room=" + roomCode
        if (password.isBlank()) return base
        return try {
            base + "#pw=" + URLEncoder.encode(password, "UTF-8")
        } catch (_: Exception) {
            base
        }
    }

    // Pull the 6-letter room code out of a pasted link:
    //   https://host/?room=ABC123#pw=secret
    //   https://host/watch/ABC123
    //   ABC123
    // Returns null when there is no code to read. A #pw= fragment
    // on the link is ignored here - see parsePasswordFromLink.
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

    // Pull the password out of a pasted link:
    //   https://host/?room=ABC123#pw=secret
    // Returns null when the link carries no password. The
    // fragment is URL-decoded; a ?pw= query value is accepted
    // too, for links built that way.
    fun parsePasswordFromLink(text: String): String? {
        val input = text.trim()
        if (input.isEmpty()) return null
        return try {
            val uri = Uri.parse(input)
            val fromFragment = uri.fragment
            if (fromFragment != null && fromFragment.startsWith("pw=")) {
                val raw = fromFragment.removePrefix("pw=").substringBefore("&")
                val decoded = try {
                    URLDecoder.decode(raw, "UTF-8")
                } catch (_: Exception) {
                    raw
                }
                if (decoded.isNotBlank()) decoded else null
            } else {
                uri.getQueryParameter("pw")?.takeIf { it.isNotBlank() }
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

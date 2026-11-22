// FILE: app/src/main/java/com/calnyx/app/network/CameraLivePrefs.kt
// NEW FILE - Camera Live settings (2026-11-21). Task 5, Camera Live 1-to-1.
// FIX 2026-11-21: passwords and watch sessions are MEMORY ONLY, never
// SharedPreferences. Relay URL, quality and facing are still saved in
// SharedPreferences exactly as before. Any password / watch keys saved
// on disk by an earlier build are wiped on read (see
// wipeLegacyPasswordKeys). Password minimum is 4 characters.
// Mirrors the 02-stream-link-top ScreenStreamPrefs (3-arg
// streamLink with #pw=, parsePasswordFromLink) - the only
// differences are the Camera Live rules:
//   * password minimum 4 characters (Screen Stream now also 4 -
//     MIN_PASSWORD_LENGTH = 4 in both files)
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
    private const val KEY_QUALITY = "quality"
    private const val KEY_FACING = "facing"

    // Camera Live password: at least 4 characters. Enforced here
    // in the UI, in CameraLiveService, and on the relay.
    const val MIN_PASSWORD_LENGTH = 4

    // Passwords are MEMORY ONLY, never SharedPreferences. The host
    // password lives only until the process dies, and the watch
    // session likewise. Function signatures are unchanged so
    // callers compile without any change.
    @Volatile
    private var hostPasswordInMemory: String = ""

    @Volatile
    private var watchSessionInMemory: Triple<String, String, String>? = null

    // One-time cleanup: wipe any password / watch keys an earlier
    // build saved in plaintext in SharedPreferences, so no old
    // password stays on disk. Called from the password / watch
    // reads (and writes), below.
    private fun wipeLegacyPasswordKeys(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove("password")
            .remove("watch_base")
            .remove("watch_room")
            .remove("watch_password")
            .apply()
    }

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
        wipeLegacyPasswordKeys(context)
        return hostPasswordInMemory
    }

    fun savePassword(context: Context, password: String) {
        hostPasswordInMemory = password
        wipeLegacyPasswordKeys(context)
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
    // MEMORY ONLY (2026-11-21 fix): stored in
    // watchSessionInMemory, never in SharedPreferences.
    fun saveWatchSession(context: Context, baseUrl: String, roomCode: String, password: String) {
        watchSessionInMemory = Triple(baseUrl, roomCode, password)
        wipeLegacyPasswordKeys(context)
    }

    // Triple(baseUrl, roomCode, password), or null when nothing
    // is being watched.
    fun loadWatchSession(context: Context): Triple<String, String, String>? {
        wipeLegacyPasswordKeys(context)
        val session = watchSessionInMemory ?: return null
        if (session.second.isBlank() || session.first.isBlank()) return null
        return session
    }

    fun clearWatchSession(context: Context) {
        watchSessionInMemory = null
        wipeLegacyPasswordKeys(context)
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
    // ROOM CODE: generated by the relay server (genCode). To use
    // 8-character codes, raise genCode to 8 on the RELAY
    // (server.js), not here - the app accepts any code the relay
    // returns.
    fun streamLink(relayUrl: String, roomCode: String, password: String): String {
        if (roomCode.length < 6) return relayUrl.trim().trimEnd('/')
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
    // Pull the 6-letter room code out of a pasted link:
    //   https://host/?room=ABC123
    //   https://host/watch/ABC123
    //   ABC123
    // Returns null when there is no code to read.
    // WATCH-LINK FIX (2026-11-22): room= is searched for
    // ANYWHERE in the pasted text first, so a link wrapped in
    // message words still yields its code.
    fun parseRoomCode(text: String): String? {
        val input = text.trim()
        if (input.isEmpty()) return null
        if (Regex("^[A-Za-z0-9]{6}$").matches(input)) return input.uppercase()
        Regex("room=([A-Za-z0-9]{6})", RegexOption.IGNORE_CASE).find(input)
            ?.groupValues?.getOrNull(1)?.let { return it.uppercase() }
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
    // Pull the password out of a pasted link:
    //   https://host/?room=ABC123#pw=secret  -> "secret"
    //   https://host/?room=ABC123?pw=secret   -> "secret" (fallback)
    // Returns null when the link carries no password.
    // WATCH-LINK FIX (2026-11-22): the #pw= / ?pw= value is
    // searched for ANYWHERE in the pasted text first. A link
    // copied out of WhatsApp / a message often arrives with
    // extra words around it, and Uri.parse of that whole text
    // finds no fragment at all - that was the second half of
    // the "link does not work" bug. The Uri logic below stays
    // as the fallback for clean links.
    fun parsePasswordFromLink(text: String): String? {
        val input = text.trim()
        if (input.isEmpty()) return null
        Regex("[#?&]pw=([^&\\s]+)").find(input)?.groupValues?.getOrNull(1)?.let { raw ->
            val decoded = try {
                URLDecoder.decode(raw, "UTF-8")
            } catch (_: Exception) {
                raw
            }
            if (decoded.isNotBlank()) return decoded
        }
        return try {
            val uri = Uri.parse(input)
            val frag = uri.fragment
            if (!frag.isNullOrBlank()) {
                for (part in frag.split('&')) {
                    val kv = part.split('=', limit = 2)
                    if (kv.size == 2 && kv[0] == "pw") {
                        val decoded = try {
                            URLDecoder.decode(kv[1], "UTF-8")
                        } catch (_: Exception) {
                            kv[1]
                        }
                        if (decoded.isNotBlank()) return decoded
                    }
                }
            }
            val fromQuery = uri.getQueryParameter("pw")?.trim()
            if (!fromQuery.isNullOrBlank()) fromQuery else null
        } catch (_: Exception) {
            null
        }
    }

    // The host part of a pasted link, for the in-app viewer:
    // https://host/?room=ABC123 -> https://host
    // A bare room code has no host - the saved relay is used.
    // The host part of a pasted link, for the in-app viewer:
    // https://host/?room=ABC123 -> https://host
    // A bare room code has no host - the saved relay is used.
    // WATCH-LINK FIX (2026-11-22): the host is searched for
    // ANYWHERE in the pasted text first, so a link wrapped in
    // message words still yields its host.
    fun parseBaseUrl(text: String, fallbackRelayUrl: String): String {
        val input = text.trim()
        Regex("(https?://[A-Za-z0-9.\\-]+(?::[0-9]+)?)").find(input)
            ?.groupValues?.getOrNull(1)?.let { return it.trimEnd('/') }
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

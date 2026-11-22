// FILE: app/src/main/java/com/calnyx/app/network/ScreenStreamPrefs.kt

// NEW FILE - Screen Stream settings (2026-11-21).
// FIX 2026-11-21: passwords and watch sessions are MEMORY ONLY, never
// SharedPreferences. Relay URL and quality are still saved in
// SharedPreferences exactly as before. Any password / watch keys saved
// on disk by an earlier build are wiped on read (see
// wipeLegacyPasswordKeys). Password minimum raised from 2 to 4
// characters (MIN_PASSWORD_LENGTH = 4), matching Camera Live.
// Relay URL, quality and (in CameraLivePrefs) facing are saved in
// SharedPreferences, same pattern as the other CalNyx prefs files.
// Password is mandatory and at least 4 characters - the service
// and the relay server both enforce it.
package com.calnyx.app.thirdeye

import android.content.Context
import android.net.Uri
import java.net.URLDecoder
import java.net.URLEncoder

object ScreenStreamPrefs {
    private const val PREFS = "screenstream_prefs"
    private const val KEY_RELAY_URL = "relay_url"
    private const val KEY_QUALITY = "quality"

    // Screen Stream password: at least 4 characters (raised from 2
    // on 2026-11-21, matching Camera Live). Enforced here in the
    // UI, in ScreenStreamService, and on the relay.
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
    // The live Render service is "calnyx-screen-stream" - the first
    // build shipped "calnyx-stream" (missing "screen") and that host
    // answers 404, so no stream could ever start. Change this line
    // only if the relay moves to a different address.
    const val DEFAULT_RELAY_URL = "https://calnyx-screen-stream.onrender.com"

    fun savedRelayUrl(context: Context): String {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_RELAY_URL, DEFAULT_RELAY_URL) ?: DEFAULT_RELAY_URL
        // WRONG-HOST FIX (2026-11-21): the first build shipped
        // "calnyx-stream" (missing "screen") and saved it on the
        // phone. That host is not the relay - it answers 404. If
        // the saved URL is that wrong host, use the correct default.
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

    // WATCH SESSION (2026-11-21) - the room being watched, so the
    // watch survives leaving the app or the Network screen: when
    // the Network screen opens again, the viewer reopens itself
    // with the same room + password, full screen, until Stop.
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

    // Share link for a room: https://<relay>/?room=ABC123#pw=<password>
    // PASSWORD-IN-LINK (2026-11-21, user decision): the password IS
    // in the link now, as a #pw= fragment, so opening the link opens
    // the stream directly with no separate password entry. A #pw=
    // fragment is never sent to the server - it stays in the browser
    // / app. Anyone with the link can watch.
    // Screen Stream password minimum is 4 characters (raised from
    // 2 on 2026-11-21), matching Camera Live.
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

    // Pull the password out of a pasted stream link:
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

    // Pull the password out of a pasted stream link:
    //   https://host/?room=ABC123#pw=secret  -> "secret"
    //   https://host/?room=ABC123?pw=secret   -> "secret" (fallback)
    // Returns null when the link carries no password.
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

    // Pull the 6-letter room code out of a pasted link:
    //   https://host/?room=ABC123
    //   https://host/watch/ABC123
    //   ABC123
    // Returns null when there is no code to read.
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

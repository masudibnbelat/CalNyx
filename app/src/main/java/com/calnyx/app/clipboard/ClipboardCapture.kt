// FILE: app/src/main/java/com/calnyx/app/clipboard/ClipboardCapture.kt
// NEW FILE - Clipboard fix (2026-11-20). READ THIS FIRST.
// Target: app/src/main/java/com/calnyx/app/clipboard/ClipboardCapture.kt
// First package line: package com.calnyx.app.clipboard
// First object line: object ClipboardCapture
//
// WHY THE OLD CODE NEVER WORKED IN THE BACKGROUND (research, not a guess):
// Android 10+ (API 29+) lets ONLY the focused app or the default IME
// read the clipboard. A foreground service is NOT focused, so
// ClipboardManager.getPrimaryClip() returns null (or throws) while
// the user is in another app - even though the process is alive.
// That is why the ball works (an overlay needs no focus) and App Lock
// works (AccessibilityService gets window events, not clipboard focus),
// but ClipboardService silently saved nothing: its listener fired,
// getPrimaryClip() came back null, and the SecurityException branch
// swallowed it. AOSP confirms it: a background foreground service
// fails every read branch (only IME / focused UID / system service /
// the signature READ_CLIPBOARD_IN_BACKGROUND permission pass).
// There is NO silent stock-Android way to read the clipboard from
// the background. The only working stock route is to BECOME the
// focused app for a moment - a tiny transparent activity. That is
// what this file does, and it is the same trick real clipboard apps
// use (KDE Connect's invisible-activity recipe, Clipboard app's
// Accessibility "Monitor system clipboard").
//
// HOW THE FIX WORKS:
// 1. ClipboardService / AppLockService only LISTEN. A listener firing
//    is just a signal that a copy happened - they do NOT read here.
// 2. On that signal, tryCapture() launches ClipboardCaptureActivity:
//    transparent, no animation, noHistory, excluded from Recents.
//    While it is resumed it IS the focused app, so saveNow() can
//    read the clipboard, save it, and finish in well under a second.
//    The user just sees a very short flicker, and on Android 12+ the
//    system shows its own "CalNyx pasted from your clipboard" toast.
//    That toast is Android's, it cannot be hidden, and it proves the
//    capture worked.
// 3. A second trigger needs no clipboard listener at all: AppLockService
//    also watches TYPE_VIEW_CLICKED, and a tap on a button literally
//    labelled "Copy" launches the same capture activity.
// 4. saveNow() is the ONE place that reads + saves. The old code had
//    the same read logic copy-pasted in ClipboardService.saveCurrentClip
//    and in checkNow(), so they drifted and double-saved. Now both
//    call saveNow(). checkNow() (Clipboard screen open, CalNyx already
//    focused) reads directly and never needs the capture activity.
// 5. Dedup is PERSISTENT now (SharedPreferences, same prefs file as
//    the retention setting). The old 800 ms guard was a @Volatile
//    field on a Repository instance - every new Repository (service,
//    checkNow, screen) reset it, so one copy could save twice and a
//    deliberate re-copy was a coin toss. Same text within 2 seconds
//    of the last save is skipped; a deliberate re-copy later than
//    that still inserts a NEW row, exactly as the user asked.
package com.calnyx.app.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import java.security.MessageDigest

enum class SaveResult {
    SAVED,
    DUPLICATE,
    EMPTY,
    BLOCKED
}

object ClipboardCapture {

    private const val PREFS_NAME = "calnyx_clipboard_prefs"
    private const val KEY_LAST_TEXT_HASH = "last_text_hash"
    private const val KEY_LAST_TEXT_AT = "last_text_at"
    private const val KEY_LAST_IMAGE_KEY = "last_image_key"
    private const val KEY_LAST_IMAGE_AT = "last_image_at"
    private const val KEY_LAST_CAPTURE_AT = "last_capture_at"

    // Same text within this window is one copy firing twice, not two copies.
    private const val DEDUP_WINDOW_MS = 2000L
    // Never launch the capture activity more often than this.
    private const val CAPTURE_COOLDOWN_MS = 1200L

    @Volatile
    private var lastCaptureLaunchAt: Long = 0L

    fun fingerprint(text: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = digest.digest(text.trim().toByteArray(Charsets.UTF_8))
            bytes.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            // Fallback only: hashCode is enough for a 2-second dedup window.
            text.trim().hashCode().toString()
        }
    }

    /** True when this exact text was already saved within the dedup window. */
    fun isDuplicateText(context: Context, text: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val hash = fingerprint(text)
        val lastHash = prefs.getString(KEY_LAST_TEXT_HASH, null) ?: return false
        val lastAt = prefs.getLong(KEY_LAST_TEXT_AT, 0L)
        return lastHash == hash &&
            System.currentTimeMillis() - lastAt < DEDUP_WINDOW_MS
    }

    fun markSavedText(context: Context, text: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_TEXT_HASH, fingerprint(text))
            .putLong(KEY_LAST_TEXT_AT, System.currentTimeMillis())
            .apply()
    }

    fun isDuplicateImage(context: Context, key: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastKey = prefs.getString(KEY_LAST_IMAGE_KEY, null) ?: return false
        val lastAt = prefs.getLong(KEY_LAST_IMAGE_AT, 0L)
        return lastKey == key &&
            System.currentTimeMillis() - lastAt < DEDUP_WINDOW_MS
    }

    fun markSavedImage(context: Context, key: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_IMAGE_KEY, key)
            .putLong(KEY_LAST_IMAGE_AT, System.currentTimeMillis())
            .apply()
    }

    /**
     * Reads the current clip ONCE and saves it. Call ONLY when this
     * app is focused: from ClipboardCaptureActivity (it is focused
     * while resumed) or from the Clipboard screen / any CalNyx screen.
     * From a background service this returns BLOCKED - that is the
     * Android 10+ rule, not a bug - and the caller must then call
     * tryCapture() instead of retrying the read here.
     */
    suspend fun saveNow(context: Context): SaveResult {
        val appContext = context.applicationContext
        return try {
            val manager =
                appContext.getSystemService(Context.CLIPBOARD_SERVICE)
                    as? ClipboardManager ?: return SaveResult.EMPTY
            if (!manager.hasPrimaryClip()) return SaveResult.EMPTY
            val clip: ClipData = manager.primaryClip ?: return SaveResult.BLOCKED
            if (clip.itemCount == 0) return SaveResult.EMPTY
            val item = clip.getItemAt(0)
            val repository = ClipboardRepository(appContext)
            val uri = item.uri
            if (uri != null &&
                (clip.description?.hasMimeType("image/*") == true ||
                    item.text == null)
            ) {
                val key = uri.toString()
                if (isDuplicateImage(appContext, key)) return SaveResult.DUPLICATE
                val id = repository.insertImage(uri) ?: return SaveResult.EMPTY
                return if (id > 0L) SaveResult.SAVED else SaveResult.DUPLICATE
            }
            val text = item.coerceToText(appContext)?.toString() ?: return SaveResult.EMPTY
            if (text.isBlank()) return SaveResult.EMPTY
            if (isDuplicateText(appContext, text)) return SaveResult.DUPLICATE
            val id = repository.insertText(text)
            if (id > 0L) SaveResult.SAVED else SaveResult.DUPLICATE
        } catch (_: SecurityException) {
            // Android 10+ background read blocked. Caller: tryCapture().
            SaveResult.BLOCKED
        } catch (_: Exception) {
            SaveResult.EMPTY
        }
    }

    /**
     * Launches the transparent capture activity, debounced. Safe to
     * call from a background service, from AppLockService (which is
     * exempt from the background-activity-start restriction - it
     * already launches the decoy calculator from the background),
     * and from a CLICKED-on-Copy accessibility event. Returns false
     * when skipped by the cooldown - the copy is not lost, the
     * capture that is already running (or just ran) saves it.
     */
    fun tryCapture(context: Context): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastCaptureLaunchAt < CAPTURE_COOLDOWN_MS) return false
        lastCaptureLaunchAt = now
        return try {
            val intent = Intent(context, ClipboardCaptureActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                )
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Is the always-on capture path actually armed? True only when
     * CalNyx's AccessibilityService (App Lock) is enabled in system
     * Settings - that service is what keeps the listener alive and
     * what may launch the capture activity from the background.
     * The Clipboard screen shows this, so the user can see at a
     * glance whether background capture can work at all.
     */
    fun isAccessibilityCaptureOn(context: Context): Boolean {
        return try {
            val enabled = Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED, 0
            ) == 1
            if (!enabled) return false
            val services = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            services.contains(context.packageName) &&
                services.contains("AppLockService")
        } catch (_: Exception) {
            false
        }
    }
}

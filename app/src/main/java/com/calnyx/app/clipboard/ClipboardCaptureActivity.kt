// FILE: app/src/main/java/com/calnyx/app/clipboard/ClipboardCaptureActivity.kt
// NEW FILE - Clipboard fix (2026-11-20).
// Target: app/src/main/java/com/calnyx/app/clipboard/ClipboardCaptureActivity.kt
// First package line: package com.calnyx.app.clipboard
// First class line: class ClipboardCaptureActivity : Activity()
// Transparent, no-UI activity. It exists for ONE reason: while it is
// resumed, CalNyx IS the focused app, so Android 10+ allows
// ClipboardManager.getPrimaryClip() to return the real clip. It
// reads once via ClipboardCapture.saveNow(), then finishes. No
// layout, no animation, noHistory, excluded from Recents (see the
// manifest block in AndroidManifest-CLIPBOARD-ACTIVITY.xml).
// The read happens in onResume(), not onCreate(): focus is only
// guaranteed once the activity is resumed. A 1.5 s watchdog finishes
// it even if the read hangs (for example a slow image decode), so
// it can never get stuck on screen.
package com.calnyx.app.clipboard

import android.app.Activity
import android.os.Bundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ClipboardCaptureActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var saveStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // No setContentView: the translucent theme shows whatever
        // was underneath. Watchdog: never stay alive past 1.5 s.
        scope.launch {
            delay(1500L)
            if (!isFinishing) {
                try {
                    finish()
                } catch (_: Exception) {
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (saveStarted) return
        saveStarted = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    ClipboardCapture.saveNow(applicationContext)
                }
            } catch (_: Exception) {
            } finally {
                try {
                    finish()
                } catch (_: Exception) {
                }
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

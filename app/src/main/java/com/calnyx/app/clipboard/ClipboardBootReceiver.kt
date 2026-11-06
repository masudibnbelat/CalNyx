// FILE: app/src/main/java/com/calnyx/app/clipboard/ClipboardBootReceiver.kt
// NEW FILE - Clipboard feature (2026-11-06).
// Target: app/src/main/java/com/calnyx/app/clipboard/ClipboardBootReceiver.kt
// First package line: package com.calnyx.app.clipboard
// First class line: class ClipboardBootReceiver : BroadcastReceiver()
// On BOOT_COMPLETED: restarts the clipboard listener service and
// prunes expired rows, so auto-save resumes after a reboot
// without the user having to open CalNyx first.
// Note: on some OEM builds a foreground service started from a
// boot receiver is delayed or refused — in that case listening
// still resumes the first time the user opens CalNyx (MainActivity
// starts the service, and the screen's checkNow() catches any
// copy made meanwhile). See README-CLIPBOARD.md.
package com.calnyx.app.clipboard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ClipboardBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        ClipboardService.start(context.applicationContext)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                ClipboardRepository(context.applicationContext).pruneExpired()
            } catch (_: Exception) {
            }
        }
    }
}

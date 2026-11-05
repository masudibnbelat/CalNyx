// FILE: app/src/main/java/com/calnyx/app/moan/OrientationBootReceiver.kt
// NEW FILE - restore the saved orientation lock after reboot.
//
// On boot, the saved mode (up / down / auto) is read from
// MoanControlPrefs. If a locked mode was saved, it is re-applied
// through OrientationService.apply(), which writes the system
// setting and restarts the overlay service, so the orientation
// holds from boot until the user changes it back to Auto.
// In Auto there is nothing to restore.
package com.calnyx.app.moan

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class OrientationBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        try {
            val saved = MoanControlPrefs.getOrientationMode(context)
            if (saved == "up" || saved == "down") {
                OrientationService.apply(context, saved)
            }
        } catch (_: Exception) {
        }
    }
}

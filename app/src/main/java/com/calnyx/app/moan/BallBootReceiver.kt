// FILE: app/src/main/java/com/calnyx/app/moan/BallBootReceiver.kt
// NEW FILE - floating ball after reboot (2026-11-07).
// The ball service was only ever started from the Hardware
// screen, so after a reboot the ball stayed gone until the user
// opened Hardware. On BOOT_COMPLETED: if the ball toggle is ON
// and the overlay permission is granted, start the service, so
// the ball is back without opening the app. Same pattern as
// ClipboardBootReceiver. Honest limit: on some OEM builds a
// foreground service started from a boot receiver is delayed or
// refused - then the ball still comes back the first time the
// user opens CalNyx (NavGraph restores it on launch).
package com.calnyx.app.moan

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BallBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        try {
            if (!MoanControlPrefs.isBallEnabled(context)) return
            if (!FloatingBallService.canDrawOverlays(context)) return
            FloatingBallService.start(context.applicationContext)
        } catch (_: Exception) {
        }
    }
}

// FILE: app/src/main/java/com/calnyx/app/blackhole/BlackHolePickerGuard.kt
// NEW file. Import-fix guard (2026-11-06, user video 16:47):
// The system file picker (GetContent) is a SEPARATE activity, so
// opening it puts CalNyx in ON_STOP. NavGraph's privacy observer
// used to treat that as "user left the app" and popped the BlackHole
// back to the Normal calculator — the picker result then had no
// BlackHoleScreen left to return to, so the import never ran and the
// user landed back on the calculator, having to type the secret
// again. Same for Documents and Audio (both use the same picker).
// BlackHoleScreen sets this guard just before launching the picker
// (and the system delete-confirm dialog), and clears it in the
// result callback. NavGraph's ON_STOP skips the privacy close
// while the guard is open. The 10-minute expiry is a safety net:
// if a launch ever fails or a result is lost, the guard cannot
// stick forever and leave the blackhole open after a real exit.
package com.calnyx.app.blackhole

object BlackHolePickerGuard {
        @Volatile private var openedAtMs: Long = 0L

        fun begin() {
                openedAtMs = System.currentTimeMillis()
        }

        fun end() {
                openedAtMs = 0L
        }

        fun isPickerOpen(): Boolean {
                val started = openedAtMs
                if (started == 0L) return false
                if (System.currentTimeMillis() - started > 10L * 60L * 1000L) {
                        openedAtMs = 0L
                        return false
                }
                return true
        }
}

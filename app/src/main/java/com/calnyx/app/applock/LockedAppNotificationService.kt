// FILE: app/src/main/java/com/calnyx/app/applock/LockedAppNotificationService.kt
// NEW file (2026-11-07). Hides notifications from locked apps while
// they are locked: a locked app's notification must never appear in
// the shade or on the lock screen, or the lock means nothing.
// How it works: onNotificationPosted cancels any notification whose
// package is locked (App Lock enabled + package in the locked set).
// onListenerConnected sweeps notifications already sitting in the
// shade (posted before this service connected, or before the app
// was locked). CalNyx's own notifications are never cancelled, and
// non-locked apps are never touched.
// HONEST LIMIT: a cancelled notification is gone for good - Android
// does not let an app re-post another app's notification. So after
// the user unlocks the app, its OLD notifications do NOT come back;
// only NEW notifications posted after unlock will show. The hiding
// applies only while the app is locked.
// OVERLAY NOTIFICATION (2026-11-07): Android System posts
// "CalNyx is displaying over other apps" while the floating ball
// is showing. The user asked for it to be hidden. When the
// hide-overlay toggle is on (default ON), this service cancels
// that one notification - only CalNyx's own, matched by app name
// + "displaying over other apps", never another app's. The system
// re-posts it only if the overlay is removed and shown again,
// and the listener cancels it again then. Honest limit: the
// match is on the English system text; if the phone language is
// not English the text differs and the notification stays.
// SETUP: the user must grant Notification access once (Settings >
// Apps > Special access > Notification access > CalNyx). Without
// it this service never runs. isNotificationAccessGranted() checks
// that - AppLockScreen can show a matching help card (same pattern
// as its Accessibility-off card) with a button opening
// Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS when it is missing.
// MANIFEST (add inside <application>, NOT a full manifest file):
//   <service
//       android:name=".applock.LockedAppNotificationService"
//       android:label="@string/app_name"
//       android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
//       <intent-filter>
//           <action android:name="android.service.notification.NotificationListenerService" />
//       </intent-filter>
//   </service>
package com.calnyx.app.applock

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat

class LockedAppNotificationService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn ?: return
        val pkg = notification.packageName
        // Never hide CalNyx's own notifications.
        if (pkg == packageName) return
        // HIDE OVERLAY NOTIFICATION: runs even when App Lock is
        // off - it has nothing to do with locking. Only CalNyx's
        // own "displaying over other apps" notice is cancelled.
        try {
            if (isOverlayNotificationHidden(this) && isOverlayNotification(notification)) {
                cancelNotification(notification.key)
                return
            }
        } catch (_: Exception) {
        }
        try {
            if (!AppLockManager.isEnabled(this)) return
            // isLocked() is exactly "pkg is in the locked set".
            if (!AppLockManager.isLocked(this, pkg)) return
            cancelNotification(notification.key)
        } catch (_: Exception) {
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        // Sweep the overlay notice too, in case it is already
        // sitting in the shade when the listener connects.
        try {
            if (isOverlayNotificationHidden(this)) {
                for (sbn in activeNotifications) {
                    try {
                        if (isOverlayNotification(sbn)) cancelNotification(sbn.key)
                    } catch (_: Exception) {
                    }
                }
            }
        } catch (_: Exception) {
        }
        // Hide notifications already sitting in the shade (posted
        // before this service connected, or before the app was locked).
        try {
            if (!AppLockManager.isEnabled(this)) return
            val locked = AppLockManager.getLockedPackages(this)
            if (locked.isEmpty()) return
            for (sbn in activeNotifications) {
                val pkg = sbn.packageName
                if (pkg == packageName) continue
                if (pkg in locked) {
                    try {
                        cancelNotification(sbn.key)
                    } catch (_: Exception) {
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    // True when this is Android System's "X is displaying over
    // other apps" notice for OUR app only: posted by the "android"
    // package, naming CalNyx. Another app's overlay notice is
    // never matched, so it is never touched.
    private fun isOverlayNotification(sbn: StatusBarNotification): Boolean {
        if (sbn.packageName != "android") return false
        return try {
            val extras = sbn.notification.extras
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            val combined = "$title $text"
            val appName = try {
                packageManager.getApplicationLabel(applicationInfo).toString()
            } catch (_: Exception) {
                "CalNyx"
            }
            combined.contains("displaying over other apps", ignoreCase = true) &&
                combined.contains(appName, ignoreCase = true)
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        private const val PREFS_HIDE = "notification_hide_prefs"
        private const val KEY_HIDE_OVERLAY = "hide_overlay_notification"

        // The overlay-notice toggle (default ON - the user asked
        // for the notice to be hidden). Read by the intro + About
        // list; written by the toggle there.
        fun isOverlayNotificationHidden(context: Context): Boolean {
            return try {
                context.getSharedPreferences(PREFS_HIDE, Context.MODE_PRIVATE)
                    .getBoolean(KEY_HIDE_OVERLAY, true)
            } catch (_: Exception) {
                true
            }
        }

        fun setOverlayNotificationHidden(context: Context, hidden: Boolean) {
            try {
                context.getSharedPreferences(PREFS_HIDE, Context.MODE_PRIVATE)
                    .edit().putBoolean(KEY_HIDE_OVERLAY, hidden).apply()
            } catch (_: Exception) {
            }
        }

        // True when the user granted Notification access to CalNyx
        // (Settings > Apps > Special access > Notification access).
        // Without it this service never runs and nothing is hidden.
        fun isNotificationAccessGranted(context: Context): Boolean {
            return try {
                NotificationManagerCompat.getEnabledListenerPackages(context)
                    .contains(context.packageName)
            } catch (_: Exception) {
                false
            }
        }
    }
}

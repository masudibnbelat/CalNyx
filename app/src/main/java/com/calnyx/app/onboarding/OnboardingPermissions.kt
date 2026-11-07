// FILE: app/src/main/java/com/calnyx/app/onboarding/OnboardingPermissions.kt
// NEW FILE - every permission CalNyx needs, in one place. Both the
// first-run introduction and the About dialog use this same list, so
// the two can never drift apart.
//
// Where the list comes from: the app's AndroidManifest (FULL version).
// Only permissions the user can actually grant are listed here:
//  - Runtime permissions (system dialog): Camera, Microphone
//    (sound in Spy videos), Media (photos / videos / audio,
//    Android 13+), Notifications (Android 13+).
//  - Special settings (a system screen opens): Display over other
//    apps (floating ball), Modify system settings (orientation lock),
//    Do Not Disturb access (Silent mode), Accessibility (App Lock,
//    Power Menu, Lock Screen), Install unknown apps (in-app updates),
//    Ignore battery optimization (background services keep running),
//    Notification access (hide locked apps' notifications).
//  - Stop notifications (OPTIONAL): opens the app's notification
//    settings. CalNyx runs foreground services (Spy recording,
//    Clipboard, floating ball), so Android keeps a "running"
//    notification on top of the screen. Turning notifications off
//    hides it - the services keep running silently underneath.
//  - Hide overlay notification (TOGGLE, OPTIONAL, default ON):
//    the NotificationListenerService cancels Android System's
//    "CalNyx is displaying over other apps" notice while the
//    floating ball is showing. Needs Notification access to run;
//    flipping it on without that access opens its Settings
//    screen instead. English system text only - see the service.
//  - Overlay notification settings (SETTINGS, OPTIONAL): opens
//    the ANDROID SYSTEM notification settings, where the user
//    turns off the 'displaying over other apps' notice by hand.
//    The app cannot read another app's channel state, so this
//    item never shows as granted - the button stays available.
// NOT listed, on purpose:
//  - Install-time permissions (granted with the install, nothing to
//    tap): Internet, Foreground Service, Receive Boot Completed,
//    Vibrate, Modify Audio Settings, Wake Lock.
//  - READ_PHONE_STATE is declared in the manifest but no feature in
//    the app uses it and CalNyx never reads phone state, so it is
//    not asked for here.
// English only, black / white only (the icons are tinted with the
// theme colour by the screens that show them).
package com.calnyx.app.onboarding

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import com.calnyx.app.applock.AppLockManager
import com.calnyx.app.applock.LockedAppNotificationService

enum class PermissionKind {
    RUNTIME,
    SETTINGS,
    // Not a permission - an on/off option rendered as a Switch
    // instead of a Grant button. Never blocks the Next button.
    TOGGLE
}

data class AppPermission(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val kind: PermissionKind,
    // RUNTIME only: the Android permissions to ask for together.
    val runtimePermissions: List<String> = emptyList(),
    // SETTINGS only: the Settings screen to open.
    val settingsAction: String? = null,
    // Optional items never block the Next button.
    val optional: Boolean = false
)

object OnboardingPermissions {

    const val ID_STOP_NOTIFICATIONS = "stop_notifications"
    const val ID_HIDE_OVERLAY_NOTIFICATION = "hide_overlay_notification"
    const val ID_OVERLAY_NOTIFICATION_SETTINGS = "overlay_notification_settings"

    fun all(): List<AppPermission> = listOf(
        AppPermission(
            id = "camera",
            title = "Camera",
            description = "Spy records secret videos with the front or back camera.",
            icon = Icons.Default.CameraAlt,
            kind = PermissionKind.RUNTIME,
            runtimePermissions = listOf(Manifest.permission.CAMERA)
        ),
        AppPermission(
            id = "microphone",
            title = "Microphone",
            description = "Records sound with Spy videos, so recordings have audio.",
            icon = Icons.Default.Mic,
            kind = PermissionKind.RUNTIME,
            runtimePermissions = listOf(Manifest.permission.RECORD_AUDIO)
        ),
        AppPermission(
            id = "media",
            title = "Photos, videos & audio",
            description = "Add photos, videos, documents and audio to the hidden Black Hole vault.",
            icon = Icons.Default.PhotoLibrary,
            kind = PermissionKind.RUNTIME,
            runtimePermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                listOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_AUDIO
                )
            } else {
                emptyList()
            }
        ),
        AppPermission(
            id = "notifications",
            title = "Notifications",
            description = "Shows the recording and clipboard status while CalNyx works in the background.",
            icon = Icons.Default.Notifications,
            kind = PermissionKind.RUNTIME,
            runtimePermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                listOf(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                emptyList()
            }
        ),
        AppPermission(
            id = "notification_access",
            title = "Notification access",
            description = "Hides notifications from locked apps, so a locked app's messages never show in the shade or on the lock screen.",
            icon = Icons.Default.VisibilityOff,
            kind = PermissionKind.SETTINGS,
            settingsAction = Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
        ),
        AppPermission(
            id = ID_HIDE_OVERLAY_NOTIFICATION,
            title = "Hide overlay notification",
            description = "Hides the Android System notification 'CalNyx is displaying over other apps' while the floating ball is showing. Needs Notification access to work.",
            icon = Icons.Default.NotificationsOff,
            kind = PermissionKind.TOGGLE,
            optional = true
        ),
        AppPermission(
            id = ID_OVERLAY_NOTIFICATION_SETTINGS,
            title = "Overlay notification settings",
            description = "Opens the Android System notification settings - turn off the 'displaying over other apps' notification there.",
            icon = Icons.Default.Settings,
            kind = PermissionKind.SETTINGS,
            settingsAction = Settings.ACTION_APP_NOTIFICATION_SETTINGS,
            optional = true
        ),
        AppPermission(
            id = "overlay",
            title = "Display over other apps",
            description = "Shows the floating ball on top of every app, even the lock screen.",
            icon = Icons.Default.Layers,
            kind = PermissionKind.SETTINGS,
            settingsAction = Settings.ACTION_MANAGE_OVERLAY_PERMISSION
        ),
        AppPermission(
            id = "write_settings",
            title = "Modify system settings",
            description = "Locks the screen orientation (up, down or auto) everywhere, even outside the app.",
            icon = Icons.Default.Settings,
            kind = PermissionKind.SETTINGS,
            settingsAction = Settings.ACTION_MANAGE_WRITE_SETTINGS
        ),
        AppPermission(
            id = "dnd",
            title = "Do Not Disturb access",
            description = "Lets Sound switch the phone to Silent mode.",
            icon = Icons.Default.VolumeOff,
            kind = PermissionKind.SETTINGS,
            settingsAction = Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS
        ),
        AppPermission(
            id = "accessibility",
            title = "Accessibility",
            description = "Powers App Lock, the Power Menu and the Lock Screen button. Turn on CalNyx in Settings > Accessibility > Downloaded apps.",
            icon = Icons.Default.AccessibilityNew,
            kind = PermissionKind.SETTINGS,
            settingsAction = Settings.ACTION_ACCESSIBILITY_SETTINGS
        ),
        AppPermission(
            id = "install_updates",
            title = "Install app updates",
            description = "Lets CalNyx install its own updates when you tap Check for updates.",
            icon = Icons.Default.SystemUpdate,
            kind = PermissionKind.SETTINGS,
            settingsAction = Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES
        ),
        AppPermission(
            id = "battery",
            title = "Ignore battery optimization",
            description = "Keeps the clipboard, the ball and the orientation lock running in the background.",
            icon = Icons.Default.BatteryChargingFull,
            kind = PermissionKind.SETTINGS,
            settingsAction = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
        ),
        AppPermission(
            id = ID_STOP_NOTIFICATIONS,
            title = "Stop notifications",
            description = "CalNyx runs in the background, so Android shows a running notification. Turn notifications off and it keeps running without showing anything.",
            icon = Icons.Default.NotificationsOff,
            kind = PermissionKind.SETTINGS,
            settingsAction = Settings.ACTION_APP_NOTIFICATION_SETTINGS,
            optional = true
        )
    )

    fun required(): List<AppPermission> = all().filter { !it.optional }

    // True when the permission is already granted (or not needed on
    // this Android version - e.g. media / notifications below 13).
    fun isGranted(context: Context, permission: AppPermission): Boolean {
        return try {
            when (permission.id) {
                "camera", "microphone", "media", "notifications" -> {
                    if (permission.runtimePermissions.isEmpty()) true
                    else permission.runtimePermissions.all {
                        ContextCompat.checkSelfPermission(context, it) ==
                            PackageManager.PERMISSION_GRANTED
                    }
                }
                "overlay" -> Settings.canDrawOverlays(context)
                "write_settings" -> Settings.System.canWrite(context)
                "dnd" -> {
                    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.isNotificationPolicyAccessGranted
                }
                "accessibility" -> AppLockManager.isAccessibilityEnabled(context)
                "install_updates" -> context.packageManager.canRequestPackageInstalls()
                "notification_access" -> LockedAppNotificationService.isNotificationAccessGranted(context)
                ID_HIDE_OVERLAY_NOTIFICATION -> LockedAppNotificationService.isOverlayNotificationHidden(context)
                // Never granted: another app's channel state cannot
                // be read, so the button always stays available.
                ID_OVERLAY_NOTIFICATION_SETTINGS -> false
                "battery" -> {
                    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                    pm.isIgnoringBatteryOptimizations(context.packageName)
                }
                // Literal, like the other branches (the value is
                // OnboardingPermissions.ID_STOP_NOTIFICATIONS).
                // Written as "x == false" on purpose: a statement
                // starting with "!" in this when-branch block made
                // this project's compiler report "Unexpected token"
                // at the "!" (kspDebugKotlin, line 202).
                "stop_notifications" -> {
                    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.areNotificationsEnabled() == false
                }
                else -> false
            }
        } catch (_: Exception) {
            false
        }
    }

    // TOGGLE items: flip the option. Flipping the overlay hide ON
    // needs the listener running - if Notification access is
    // missing, its Settings screen opens instead and the toggle
    // stays off; the list refreshes on return.
    fun setToggle(context: Context, permission: AppPermission, value: Boolean) {
        try {
            when (permission.id) {
                ID_HIDE_OVERLAY_NOTIFICATION -> {
                    if (value && !LockedAppNotificationService.isNotificationAccessGranted(context)) {
                        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        context.startActivity(intent)
                        return
                    }
                    LockedAppNotificationService.setOverlayNotificationHidden(context, value)
                }
            }
        } catch (_: Exception) {
        }
    }

    // id -> granted, for every permission in the list.
    fun checkAll(context: Context): Map<String, Boolean> =
        all().associate { it.id to isGranted(context, it) }

    fun allRequiredGranted(context: Context): Boolean =
        required().all { isGranted(context, it) }

    // The Intent to open for a SETTINGS permission, or null for a
    // RUNTIME one (those go through the permission launcher).
    // Callers must catch the startActivity themselves.
    fun settingsIntent(context: Context, permission: AppPermission): Intent? {
        val action = permission.settingsAction ?: return null
        return Intent(action).apply {
            when (permission.id) {
                "overlay", "write_settings", "install_updates", "battery" ->
                    data = Uri.parse("package:${context.packageName}")
                ID_STOP_NOTIFICATIONS ->
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                ID_OVERLAY_NOTIFICATION_SETTINGS ->
                    // The notice is posted by Android System, so
                    // open ITS notification settings, not CalNyx's.
                    putExtra(Settings.EXTRA_APP_PACKAGE, "android")
            }
        }
    }
}

package com.calnyx.app.launcher

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

data class LauncherAppInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable
)

object LauncherAppsLoader {
    fun load(context: Context): List<LauncherAppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = pm.queryIntentActivities(intent, 0)
        val result = ArrayList<LauncherAppInfo>()
        val seen = HashSet<String>()
        for (info in resolveInfos) {
            val packageName = info.activityInfo.packageName
            if (!seen.add(packageName)) {
                continue
            }
            val label = try {
                info.loadLabel(pm).toString()
            } catch (e: Exception) {
                packageName
            }
            val icon = try {
                info.loadIcon(pm)
            } catch (e: Exception) {
                continue
            }
            result.add(LauncherAppInfo(packageName, label, icon))
        }
        result.sortWith(compareBy { it.label.lowercase() })
        return result
    }

    fun drawableToBitmap(drawable: Drawable): ImageBitmap {
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 128
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 128
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(canvas)
        return bitmap.asImageBitmap()
    }

    fun launch(context: Context, packageName: String) {
        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
            }
        } catch (e: Exception) {
            // App cannot be launched, ignore silently.
        }
    }

    fun openAppInfo(context: Context, packageName: String) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.fromParts("package", packageName, null)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            // Settings cannot be opened, ignore silently.
        }
    }

    fun uninstall(context: Context, packageName: String) {
        try {
            val intent = Intent(Intent.ACTION_DELETE)
            intent.data = Uri.fromParts("package", packageName, null)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            // Uninstall cannot be started, ignore silently.
        }
    }

    fun openDialer(context: Context) {
        try {
            val intent = Intent(Intent.ACTION_DIAL)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            // Dialer cannot be opened, ignore silently.
        }
    }

    fun openCamera(context: Context) {
        try {
            val intent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            // Camera cannot be opened, ignore silently.
        }
    }

    fun openSystemSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            // Settings cannot be opened, ignore silently.
        }
    }
}

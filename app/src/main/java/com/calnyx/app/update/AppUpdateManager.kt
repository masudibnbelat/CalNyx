package com.calnyx.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

// A newer release found on GitHub.
data class UpdateInfo(
    val version: String,
    val downloadUrl: String,
    val notes: String,
    val sizeBytes: Long
)

// GitHub-Releases based updater (same scheme as Tk Koi Gelo):
// check releases/latest -> compare with the installed version ->
// download the attached APK -> hand it to the system installer.
object AppUpdateManager {

    private const val RELEASES_URL =
        "https://api.github.com/repos/masudibnbelat/CalNyx/releases/latest"

    @Suppress("DEPRECATION")
    fun currentVersion(context: Context): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"
        } catch (e: Exception) {
            "0"
        }
    }

    // null = already up to date; failure = could not check.
    suspend fun checkForUpdate(context: Context): Result<UpdateInfo?> =
        withContext(Dispatchers.IO) {
            try {
                val connection = URL(RELEASES_URL).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.setRequestProperty("User-Agent", "CalNyx")
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    return@withContext Result.failure(
                        Exception("GitHub returned ${connection.responseCode}")
                    )
                }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val latest = json.optString("tag_name").trim()
                    .removePrefix("v").removePrefix("V")
                if (latest.isEmpty()) {
                    return@withContext Result.success(null)
                }
                if (compareVersions(latest, currentVersion(context)) <= 0) {
                    return@withContext Result.success(null)
                }
                val assets = json.optJSONArray("assets")
                var apkAsset: JSONObject? = null
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                            apkAsset = asset
                            break
                        }
                    }
                }
                val asset = apkAsset ?: return@withContext Result.failure(
                    Exception("No APK attached to the release")
                )
                Result.success(
                    UpdateInfo(
                        version = latest,
                        downloadUrl = asset.optString("browser_download_url"),
                        notes = json.optString("body").trim(),
                        sizeBytes = asset.optLong("size")
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun downloadApk(
        context: Context,
        info: UpdateInfo,
        onProgress: (Float) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val file = File(dir, "CalNyx-${info.version}.apk")
        val connection = URL(info.downloadUrl).openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", "CalNyx")
        connection.connectTimeout = 15000
        connection.readTimeout = 30000
        val total = if (info.sizeBytes > 0) {
            info.sizeBytes
        } else {
            connection.contentLength.toLong()
        }
        connection.inputStream.use { input ->
            file.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var downloaded = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    downloaded += read
                    if (total > 0) {
                        onProgress((downloaded.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        }
        onProgress(1f)
        file
    }

    fun installApk(context: Context, file: File) {
        // Android 8+: the app needs the "install unknown apps" grant
        // first — send the user to that settings page, they come back
        // and tap Install again.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(settingsIntent)
            return
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    // Numeric segment compare: "1.10.0" is newer than "1.9.0".
    fun compareVersions(a: String, b: String): Int {
        val partsA = a.split('.').map { it.toIntOrNull() ?: 0 }
        val partsB = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(partsA.size, partsB.size)) {
            val x = partsA.getOrElse(i) { 0 }
            val y = partsB.getOrElse(i) { 0 }
            if (x != y) return x - y
        }
        return 0
    }
}

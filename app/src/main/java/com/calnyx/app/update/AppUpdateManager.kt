// UPDATE FIX (2026-11-20) - REPLACE.
// The GitHub API check is unauthenticated, so GitHub allows only
// 60 requests/hour per IP. On mobile data the IP is shared, the
// quota runs out and the API answers 403 - the dialog then showed
// "GitHub returned 403" on phones that did nothing wrong.
// Fix (no token - a token baked into the APK can be extracted, and
// every user would share its quota): the API stays the first check
// (it returns the notes, the exact APK asset URL and its size), but
// when it fails at the HTTP/network level the check falls back to
// the GitHub web page https://github.com/.../releases/latest,
// which has no API quota. That page answers 302 to
// /releases/tag/<tag>; the tag is read from the Location header,
// compared with the installed version, and the download link is
// built directly (the release APK is always CalNyx-<tag>.apk,
// verified on v3.0.8). Notes are empty and size 0 on this path -
// the dialog already hides both in that case, and downloadApk
// falls back to contentLength for progress. The dialog now only
// shows an error when GitHub itself is unreachable.
// Public API UNCHANGED (currentVersion / checkForUpdate /
// downloadApk / installApk / compareVersions / UpdateInfo).
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

    // GitHub web page for the latest release - no API quota.
    // Answers 302 to /releases/tag/<tag> when a release exists.
    private const val LATEST_PAGE_URL =
        "https://github.com/masudibnbelat/CalNyx/releases/latest"

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
            // 1) GitHub API first - full info (notes, exact asset URL,
            //    size). 2) If it fails at the HTTP/network level
            //    (403 rate limit on a shared mobile IP is the usual
            //    one), fall back to the GitHub web page. A definitive
            //    API answer (up to date, or a release with no APK) is
            //    returned as-is and never falls back.
            checkViaApi(context) ?: checkViaWeb(context)
        }

    // Returns null when the API could not be reached at the
    // HTTP/network level - the caller then tries the web fallback.
    // A non-null result is definitive: up to date, an update with
    // full info, or a real failure (e.g. release has no APK).
    private fun checkViaApi(context: Context): Result<UpdateInfo?>? {
        return try {
            val connection = URL(RELEASES_URL).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "CalNyx")
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return null
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val latest = json.optString("tag_name").trim()
                .removePrefix("v").removePrefix("V")
            if (latest.isEmpty()) {
                return Result.success(null)
            }
            if (compareVersions(latest, currentVersion(context)) <= 0) {
                return Result.success(null)
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
            val asset = apkAsset ?: return Result.failure(
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
            null
        }
    }

    // Web fallback: no API quota. GET /releases/latest with
    // redirects off and read the tag from the Location header.
    private fun checkViaWeb(context: Context): Result<UpdateInfo?> {
        return try {
            val connection = URL(LATEST_PAGE_URL).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", "CalNyx")
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            // 302 -> /releases/tag/<tag>. A 200 here means the repo
            // has no releases at all.
            val location = connection.getHeaderField("Location") ?: ""
            val tag = location.substringAfterLast("/releases/tag/", "")
                .substringBefore("?").substringBefore("#").trim()
            if (tag.isEmpty()) {
                return Result.failure(Exception("Could not check for updates"))
            }
            val latest = tag.removePrefix("v").removePrefix("V")
            if (compareVersions(latest, currentVersion(context)) <= 0) {
                return Result.success(null)
            }
            Result.success(
                UpdateInfo(
                    version = latest,
                    downloadUrl = "https://github.com/masudibnbelat/CalNyx/releases/download/$tag/CalNyx-$tag.apk",
                    notes = "",
                    sizeBytes = 0
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

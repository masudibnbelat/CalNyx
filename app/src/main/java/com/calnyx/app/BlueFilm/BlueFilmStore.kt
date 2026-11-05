// FILE: app/src/main/java/com/calnyx/app/BlueFilm/BlueFilmStore.kt
// NEW FILE - where Blue Film recordings live and how they
// leave the app.
//
// Recordings are kept in the app's PRIVATE filesDir/bluefilm/
// as .bfenc files (AES-encrypted, see BlueFilmCrypto), so they
// NEVER appear in the gallery or in any file manager. The Blue
// Film page lists them like a folder. From there a video can be:
//  - Played: decrypted to a temp .mp4 in cache and opened with
//    any video player on the phone (FileProvider grants it
//    temporary read access).
//  - Downloaded to the gallery: decrypted and saved as a normal
//    .mp4 in Movies/CalNyx (MediaStore on Android 10+, the
//    legacy public Movies folder on Android 9).
//  - Deleted: the encrypted file is removed.
package com.calnyx.app.BlueFilm

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BlueFilmStore {

    const val EXT = ".bfenc"
    private const val DIR = "bluefilm"

    fun dir(context: Context): File =
        File(context.filesDir, DIR).apply { if (!exists()) mkdirs() }

    fun listVideos(context: Context): List<File> {
        val files = dir(context).listFiles()
            ?.filter { it.isFile && it.name.endsWith(EXT) }
            ?: emptyList()
        return files.sortedByDescending { it.lastModified() }
    }

    fun newVideoFile(context: Context): File {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return File(dir(context), "BF_$stamp$EXT")
    }

    fun displayName(file: File): String = file.name.removeSuffix(EXT)

    fun displayDate(file: File): String =
        SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US)
            .format(Date(file.lastModified()))

    fun sizeText(file: File): String {
        val bytes = file.length()
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1.0) String.format(Locale.US, "%.1f MB", mb)
        else String.format(Locale.US, "%d KB", bytes / 1024)
    }

    fun deleteVideo(file: File): Boolean =
        try {
            file.delete()
        } catch (_: Exception) {
            false
        }

    // Decrypt to a temp .mp4 in cache and open it with any
    // video player the user has.
    fun playVideo(context: Context, encrypted: File): Boolean {
        return try {
            val tmp = File(context.cacheDir, displayName(encrypted) + ".mp4")
            BlueFilmCrypto.decryptFile(context, encrypted, tmp)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                tmp
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/mp4")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    // Decrypt and save a normal .mp4 to the gallery
    // (Movies/CalNyx), so it shows up in Photos / Files.
    fun exportToGallery(context: Context, encrypted: File): Boolean {
        return try {
            val tmp = File(context.cacheDir, displayName(encrypted) + ".mp4")
            BlueFilmCrypto.decryptFile(context, encrypted, tmp)
            val fileName = displayName(encrypted) + ".mp4"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(
                        MediaStore.Video.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_MOVIES + "/CalNyx"
                    )
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    values
                ) ?: return false
                resolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(tmp).use { input -> input.copyTo(out) }
                }
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                true
            } else {
                // Android 9: the legacy public Movies folder.
                val movies = File(
                    Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_MOVIES
                    ),
                    "CalNyx"
                ).apply { if (!exists()) mkdirs() }
                val dest = File(movies, fileName)
                FileInputStream(tmp).use { input ->
                    FileOutputStream(dest).use { output -> input.copyTo(output) }
                }
                context.sendBroadcast(
                    Intent(
                        Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                        Uri.fromFile(dest)
                    )
                )
                true
            }
        } catch (_: Exception) {
            false
        }
    }
}

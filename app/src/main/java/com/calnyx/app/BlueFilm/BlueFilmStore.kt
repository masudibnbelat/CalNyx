// FILE: app/src/main/java/com/calnyx/app/BlueFilm/BlueFilmStore.kt
// FOLDERS VERSION - replace your old BlueFilmStore.kt with this file.
// Based on V8.2. Only addition: deleteFolder() + renameFolder()
// (see below, after createFolder). Everything else is byte-identical.
// The DEFAULT folder ("My Blue Film") can never be deleted or
// renamed - it is where new recordings go, and listFolders()
// always recreates it.
// REPLACE the V6 version. V7 changes - Play gave ZERO
// feedback on tap (user's video: tap on Play, ripple only,
// no note, no Toast, no player, even 1.5s later):
//  1. resolveActivity REMOVED. On Android 11+ the manifest
//     <queries> only declares MAIN/LAUNCHER intents, so
//     resolveActivity() for a video VIEW intent can return
//     null even when VLC / Gallery ARE installed - and the
//     old code then returned "no_player" without ever
//     calling startActivity(). Now startActivity() is
//     called directly in try/catch: if no player exists it
//     throws ActivityNotFoundException and that becomes
//     "no_player". startActivity with an implicit intent is
//     NOT blocked by package visibility, so the player
//     chooser opens at once.
//  2. Decrypts are SERIALIZED (synchronized). The background
//     thumbnail prep and a Play tap used to decrypt the same
//     file at the same time, both deleting and rewriting the
//     SAME temp .mp4 path - the tap could wait on, or fight
//     with, the prep. Now only one decrypt runs at a time
//     and prepareTemp checks the cache inside the same lock,
//     so a tap reuses a finished temp instead of starting
//     a second decrypt.
//  Everything else from V6 is kept: folders, cached temp,
//  thumbnails, Rename, Delete dropping cache.
package com.calnyx.app.BlueFilm

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
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
    const val DEFAULT_FOLDER = "My Blue Film"
    private const val DIR = "bluefilm"

    fun dir(context: Context): File =
        File(context.filesDir, DIR).apply { if (!exists()) mkdirs() }

    private fun sanitizeName(raw: String): String? {
        val clean = raw.trim()
            .replace(Regex("[/\\\\:*?\"<>|]"), "")
            .replace(Regex("\\s+"), " ")
            .take(40)
            .trim()
        if (clean.isEmpty() || clean == "." || clean == "..") return null
        return clean
    }

    fun folderDir(context: Context, folder: String): File {
        val name = sanitizeName(folder) ?: DEFAULT_FOLDER
        return File(dir(context), name).apply { if (!exists()) mkdirs() }
    }

    // Move loose root .bfenc files into the permanent folder.
    fun ensureMigrated(context: Context) {
        val root = dir(context)
        val target = folderDir(context, DEFAULT_FOLDER)
        root.listFiles()
            ?.filter { it.isFile && it.name.endsWith(EXT) }
            ?.forEach { file ->
                var dest = File(target, file.name)
                var i = 1
                while (dest.exists()) {
                    dest = File(target, file.name.removeSuffix(EXT) + "_$i$EXT")
                    i++
                }
                try {
                    if (!file.renameTo(dest)) {
                        file.copyTo(dest, overwrite = false)
                        file.delete()
                    }
                } catch (_: Exception) {}
            }
    }

    fun listFolders(context: Context): List<String> {
        ensureMigrated(context)
        folderDir(context, DEFAULT_FOLDER)
        val folders = dir(context).listFiles()
            ?.filter { it.isDirectory }
            ?.map { it.name }
            ?.sorted()
            ?: emptyList()
        val rest = folders.filter { it != DEFAULT_FOLDER }
        return listOf(DEFAULT_FOLDER) + rest
    }

    fun folderCount(context: Context, folder: String): Int =
        listVideosInFolder(context, folder).size

    fun listVideosInFolder(context: Context, folder: String): List<File> {
        val files = folderDir(context, folder).listFiles()
            ?.filter { it.isFile && it.name.endsWith(EXT) && it.length() > 28 }
            ?: emptyList()
        return files.sortedByDescending { it.lastModified() }
    }

    // All videos, across all folders.
    fun listVideos(context: Context): List<File> {
        return listFolders(context)
            .flatMap { listVideosInFolder(context, it) }
            .sortedByDescending { it.lastModified() }
    }

    fun createFolder(context: Context, rawName: String): Boolean {
        val name = sanitizeName(rawName) ?: return false
        val f = File(dir(context), name)
        if (f.exists()) return f.isDirectory
        return try {
            f.mkdirs()
        } catch (_: Exception) {
            false
        }
    }

    // Delete a whole folder with every video inside it. The
    // DEFAULT folder can never be deleted - it is where new
    // recordings go, and listFolders always recreates it.
    // Cached decrypted temps + thumbnails of its videos are
    // dropped too, so no stale preview survives the folder.
    fun deleteFolder(context: Context, folder: String): Boolean {
        val name = sanitizeName(folder) ?: return false
        if (name == DEFAULT_FOLDER) return false
        val f = File(dir(context), name)
        if (!f.exists()) return false
        if (!f.isDirectory) return false
        return try {
            listVideosInFolder(context, name).forEach { dropCache(context, it) }
            f.deleteRecursively()
        } catch (_: Exception) {
            false
        }
    }

    // Rename a folder. Reuses sanitizeName, so the same rules
    // apply as createFolder. The DEFAULT folder can never be
    // renamed. False when the new name is blank, already taken,
    // or the rename fails. Renaming to the same name is a no-op
    // success, like renameVideo.
    fun renameFolder(context: Context, oldName: String, rawNewName: String): Boolean {
        val old = sanitizeName(oldName) ?: return false
        if (old == DEFAULT_FOLDER) return false
        val clean = sanitizeName(rawNewName) ?: return false
        if (clean == old) return true
        val src = File(dir(context), old)
        if (!src.exists() || !src.isDirectory) return false
        val dest = File(dir(context), clean)
        if (dest.exists()) return false
        return try {
            if (src.renameTo(dest)) true
            else {
                src.copyRecursively(dest, overwrite = false)
                src.deleteRecursively()
            }
        } catch (_: Exception) {
            false
        }
    }

    fun moveVideo(context: Context, file: File, targetFolder: String): Boolean {
        val target = sanitizeName(targetFolder) ?: return false
        if (file.parentFile?.name == target) return false
        if (!file.exists()) return false
        val destDir = folderDir(context, target)
        var dest = File(destDir, file.name)
        var i = 1
        while (dest.exists()) {
            dest = File(destDir, file.name.removeSuffix(EXT) + "_$i$EXT")
            i++
        }
        return try {
            val ok = if (file.renameTo(dest)) true
            else {
                file.copyTo(dest, overwrite = false)
                file.delete()
            }
            if (ok) dropCache(context, file)
            ok
        } catch (_: Exception) {
            false
        }
    }

    fun newVideoFile(context: Context): File {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return File(folderDir(context, DEFAULT_FOLDER), "BF_$stamp$EXT")
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

    // Drops the cached decrypted temp + thumbnail too, so a
    // deleted video never leaves a stale preview behind.
    fun deleteVideo(context: Context, file: File): Boolean {
        return try {
            dropCache(context, file)
            file.delete()
        } catch (_: Exception) {
            false
        }
    }

    private fun dropCache(context: Context, file: File) {
        try { File(context.cacheDir, displayName(file) + ".mp4").delete() } catch (_: Exception) {}
        try { File(context.cacheDir, "thumb_" + displayName(file) + ".jpg").delete() } catch (_: Exception) {}
    }

    fun renameVideo(context: Context, file: File, newNameRaw: String): File? {
        val clean = sanitizeName(newNameRaw) ?: return null
        if (clean == displayName(file)) return file
        val dest = File(file.parentFile, clean + EXT)
        if (dest.exists()) return null
        return try {
            val ok = if (file.renameTo(dest)) true
            else {
                file.copyTo(dest, overwrite = false)
                file.delete()
            }
            if (!ok) return null
            dropCache(context, file)
            dest
        } catch (_: Exception) {
            null
        }
    }

    private fun tempFile(context: Context, encrypted: File): File =
        File(context.cacheDir, displayName(encrypted) + ".mp4")

    private fun thumbFile(context: Context, encrypted: File): File =
        File(context.cacheDir, "thumb_" + displayName(encrypted) + ".jpg")

    private fun decryptedTemp(context: Context, encrypted: File): File? {
        if (!encrypted.exists() || encrypted.length() <= 28) return null
        // One decrypt at a time: the background thumbnail prep
        // and a Play tap must never decrypt the same file into
        // the same temp path concurrently.
        synchronized(this) {
            return try {
                val tmp = tempFile(context, encrypted)
                if (tmp.exists()) tmp.delete()
                BlueFilmCrypto.decryptFile(context, encrypted, tmp)
                if (!tmp.exists() || tmp.length() <= 0) null else tmp
            } catch (_: Exception) {
                null
            }
        }
    }

    // The real decrypt exception, for an honest error note.
    fun decryptError(context: Context, encrypted: File): String? {
        if (!encrypted.exists() || encrypted.length() <= 28) return "file_too_short"
        return try {
            val tmp = File(context.cacheDir, "check_" + displayName(encrypted) + ".mp4")
            if (tmp.exists()) tmp.delete()
            BlueFilmCrypto.decryptFile(context, encrypted, tmp)
            tmp.delete()
            null
        } catch (e: Exception) {
            e.javaClass.simpleName
        }
    }

    // Reuse the decrypted temp if it is newer than the .bfenc,
    // otherwise decrypt once. Called in the background when
    // the page opens, so a later tap finds it already ready.
    // The cache check is inside the same lock as the decrypt,
    // so a tap never starts a second decrypt while the
    // background prep is still writing the temp.
    fun prepareTemp(context: Context, encrypted: File): File? {
        synchronized(this) {
            val tmp = tempFile(context, encrypted)
            if (tmp.exists() && tmp.length() > 0 && tmp.lastModified() >= encrypted.lastModified()) {
                return tmp
            }
            return decryptedTemp(context, encrypted)
        }
    }

    fun prepareThumbnail(context: Context, encrypted: File): File? {
        val thumb = thumbFile(context, encrypted)
        if (thumb.exists() && thumb.length() > 0 && thumb.lastModified() >= encrypted.lastModified()) {
            return thumb
        }
        val tmp = prepareTemp(context, encrypted) ?: return null
        return try {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(tmp.absolutePath)
                val frame = retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: return null
                val scaled = if (frame.width > 320) {
                    val h = (frame.height * 320.0 / frame.width).toInt()
                    Bitmap.createScaledBitmap(frame, 320, h, true)
                } else {
                    frame
                }
                FileOutputStream(thumb).use { out ->
                    scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
                }
                if (scaled !== frame) frame.recycle()
                if (!thumb.exists() || thumb.length() <= 0) null else thumb
            } finally {
                try { retriever.release() } catch (_: Exception) {}
            }
        } catch (_: Exception) {
            null
        }
    }

    // External player only. Returns null on success, otherwise
    // one of: "no_file", "decrypt_failed", "no_fileprovider",
    // "no_player", "start_failed".
    // No resolveActivity() check: on Android 11+ it can return
    // null for a video intent even when players ARE installed
    // (package visibility - the manifest <queries> does not
    // declare a video VIEW intent), which made Play fail
    // without ever trying. startActivity() is called directly;
    // if truly no player exists it throws
    // ActivityNotFoundException and that becomes "no_player".
    fun playVideoError(context: Context, encrypted: File): String? {
        if (!encrypted.exists() || encrypted.length() <= 28) return "no_file"
        val tmp = prepareTemp(context, encrypted) ?: return "decrypt_failed"
        val uri = try {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tmp)
        } catch (_: Exception) {
            return "no_fileprovider"
        }
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/mp4")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            null
        } catch (e: android.content.ActivityNotFoundException) {
            "no_player"
        } catch (_: Exception) {
            "start_failed"
        }
    }

    fun playVideo(context: Context, encrypted: File): Boolean =
        playVideoError(context, encrypted) == null

    fun exportToGallery(context: Context, encrypted: File): Boolean {
        return try {
            val tmp = prepareTemp(context, encrypted) ?: return false
            val fileName = displayName(encrypted) + ".mp4"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/CalNyx")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return false
                resolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(tmp).use { input -> input.copyTo(out) }
                }
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                true
            } else {
                @Suppress("DEPRECATION")
                val movies = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                    "CalNyx"
                ).apply { if (!exists()) mkdirs() }
                val dest = File(movies, fileName)
                FileInputStream(tmp).use { input ->
                    FileOutputStream(dest).use { output -> input.copyTo(output) }
                }
                context.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(dest)))
                true
            }
        } catch (_: Exception) {
            false
        }
    }
}

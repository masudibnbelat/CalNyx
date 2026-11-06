// FILE: app/src/main/java/com/calnyx/app/BlueFilm/BlueFilmStore.kt
// PLAIN VERSION (2026-11-06) - REPLACE the old BlueFilmStore.kt.
// Based on the FOLDERS version. ENCRYPTION IS GONE:
//  - Recordings are now plain .mp4 files in filesDir/bluefilm/,
//    inside folders exactly as before. No .bfenc, no AES/GCM,
//    no decrypt step anywhere: Play opens the file itself in
//    the external player, thumbnails read the file directly,
//    Download copies the file itself to Movies / CalNyx.
//  - Files still live ONLY inside the app (internal storage),
//    so they never show in the gallery or Files. The app is
//    offline, so there is no leak path - that is why the
//    encryption was removed, at the user's request.
//  - ONE-TIME MIGRATION (first open after this update):
//    ensureMigrated() converts old encrypted .bfenc videos to
//    plain .mp4 with BlueFilmCrypto (decrypt once, delete the
//    .bfenc on success), and renames the default folder
//    "My Blue Film" -> "My Spy". A .bfenc that fails to decrypt
//    (the old damaged ones) is left alone - it simply does not
//    list. BlueFilmCrypto.kt STAYS in the project for this one
//    migration only; no new recording is ever encrypted.
//  - DEFAULT_FOLDER is now "My Spy" (page renamed Spy).
//  - Everything else is unchanged: folders, New Folder, Move,
//    Rename video/folder, Delete video/folder, sizes, dates.
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

    const val EXT = ".mp4"
    const val DEFAULT_FOLDER = "My Spy"
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

    // ONE-TIME MIGRATION, first open after the plain update:
    //  (1) the default folder "My Blue Film" becomes "My Spy";
    //  (2) loose root .bfenc files move into the default folder;
    //  (3) every .bfenc video is decrypted ONCE to a plain .mp4
    //      of the same name (via .tmp, then rename) and the
    //      .bfenc is deleted only after the .mp4 is complete.
    //  A file that fails to decrypt (old damaged recordings)
    //  is left alone and simply does not list. Nothing here
    //  ever throws: one bad file must not hide the rest.
    fun ensureMigrated(context: Context) {
        val root = dir(context)
        try {
            val oldDefault = File(root, "My Blue Film")
            val newDefault = File(root, DEFAULT_FOLDER)
            if (oldDefault.exists() && oldDefault.isDirectory && !newDefault.exists()) {
                oldDefault.renameTo(newDefault)
            }
        } catch (_: Exception) {}
        val target = folderDir(context, DEFAULT_FOLDER)
        root.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".bfenc") }
            ?.forEach { file ->
                var dest = File(target, file.name.removeSuffix(".bfenc") + EXT)
                var i = 1
                while (dest.exists()) {
                    dest = File(target, file.name.removeSuffix(".bfenc") + "_$i$EXT")
                    i++
                }
                try {
                    if (!file.renameTo(dest)) {
                        file.copyTo(dest, overwrite = false)
                        file.delete()
                    }
                } catch (_: Exception) {}
            }
        // Decrypt old .bfenc videos to plain .mp4, in place.
        root.listFiles()
            ?.filter { it.isDirectory }
            ?.forEach { folder ->
                folder.listFiles()
                    ?.filter { it.isFile && it.name.endsWith(".bfenc") }
                    ?.forEach { enc ->
                        val dest = File(folder, enc.name.removeSuffix(".bfenc") + EXT)
                        if (dest.exists()) {
                            try { enc.delete() } catch (_: Exception) {}
                            return@forEach
                        }
                        val tmpDest = File(folder, dest.name + ".tmp")
                        try {
                            if (tmpDest.exists()) tmpDest.delete()
                            BlueFilmCrypto.decryptFile(context, enc, tmpDest)
                            if (tmpDest.exists() && tmpDest.length() > 0) {
                                if (tmpDest.renameTo(dest)) {
                                    enc.delete()
                                } else {
                                    tmpDest.delete()
                                }
                            } else {
                                tmpDest.delete()
                            }
                        } catch (_: Exception) {
                            try { tmpDest.delete() } catch (_: Exception) {}
                        }
                    }
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
            ?.filter { it.isFile && it.name.endsWith(EXT) && it.length() > 0 }
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
    // Cached thumbnails of its videos are dropped too, so
    // no stale preview survives the folder.
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

    // Drops the cached thumbnail too, so a deleted video
    // never leaves a stale preview behind.
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

    private fun thumbFile(context: Context, video: File): File =
        File(context.cacheDir, "thumb_" + displayName(video) + ".jpg")

    // PLAIN: there is no decrypted temp any more - the stored
    // file IS the .mp4. prepareTemp just returns the file
    // itself when it is readable, so Play / thumbnails find
    // it already ready. Kept under the old name so the Screen
    // needs no call-site change.
    fun prepareTemp(context: Context, video: File): File? {
        return if (video.exists() && video.length() > 0) video else null
    }

    fun prepareThumbnail(context: Context, video: File): File? {
        val thumb = thumbFile(context, video)
        if (thumb.exists() && thumb.length() > 0 && thumb.lastModified() >= video.lastModified()) {
            return thumb
        }
        val tmp = prepareTemp(context, video) ?: return null
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
    // one of: "no_file", "no_fileprovider", "no_player",
    // "start_failed". PLAIN: the stored .mp4 itself is handed
    // to the player through FileProvider - no decrypt, no temp.
    // No resolveActivity() check: on Android 11+ it can return
    // null for a video intent even when players ARE installed
    // (package visibility - the manifest <queries> does not
    // declare a video VIEW intent), which made Play fail
    // without ever trying. startActivity() is called directly;
    // if truly no player exists it throws
    // ActivityNotFoundException and that becomes "no_player".
    fun playVideoError(context: Context, video: File): String? {
        if (!video.exists() || video.length() <= 0) return "no_file"
        val uri = try {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", video)
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

    fun playVideo(context: Context, video: File): Boolean =
        playVideoError(context, video) == null

    fun exportToGallery(context: Context, video: File): Boolean {
        return try {
            val fileName = displayName(video) + ".mp4"
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
                    FileInputStream(video).use { input -> input.copyTo(out) }
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
                FileInputStream(video).use { input ->
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

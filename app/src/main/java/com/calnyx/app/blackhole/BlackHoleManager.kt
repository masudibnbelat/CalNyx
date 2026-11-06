// FILE: app/src/main/java/com/calnyx/app/blackhole/BlackHoleManager.kt
// OPENFIX 4 VERSION (2026-11-06) - replace your old BlackHoleManager.kt
// with this file. ENCRYPTION REMOVED by the user's choice: files are
// stored PLAIN in app internal storage (still hidden from gallery /
// Files, still deleted from the gallery on import, still protected
// by the app lock) — so opening is instant, no decrypt, no preview
// copy, no loading. Keep the file name BlackHoleManager.kt, do not keep
// both files, otherwise you will get a duplicate class error.
// BlackHoleCrypto.kt stays in the project ONLY for the one-time
// migrateEncryptedFilesToPlain() below (old encrypted files -> plain).
//
// What changed vs the OPENFIX 3 file:
// 1. importUri: PLAIN copy now (was encrypted), still with progress
//    and the .tmp-then-rename rule (one upload = one file).
// 2. restoreFile / downloadFile: plain copy out (old encrypted files
//    are decrypted on the way out via copyOut()).
// 3. New: migrateEncryptedFilesToPlain() — converts old encrypted
//    files to plain once (replaces migratePlainFilesToEncrypted).
// 4. listFiles still hides .tmp + 0-byte files (OPENFIX 3).
// 5. Delete / resolve / MediaStore logic is exactly as before.
package com.calnyx.app.blackhole

import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

enum class BlackHoleCategory(val folder: String, val title: String, val pickerMime: String) {
    PHOTOS("photos", "Photos", "image/*"),
    VIDEOS("videos", "Videos", "video/*"),
    DOCUMENTS("documents", "Documents", "*/*"),
    AUDIO("audio", "Audio", "audio/*")
}

object BlackHolePrefs {
    private const val PREFS = "blackhole_prefs"
    private const val KEY_SECRET = "blackhole_secret"
    private const val KEY_SET = "blackhole_secret_set"
    private const val KEY_ICON = "theme_icon"

    const val DEFAULT_SECRET = "112233"

    // RENAME MIGRATION (2026-11-06): the secret used to live in
    // "vault_prefs". First time the new prefs are empty, copy the
    // secret / set-flag / theme icon over from the old file once,
    // so the user's own secret keeps working and no file is lost.
    // After that the old file is never read again.
    private const val OLD_PREFS = "vault_prefs"
    private fun prefs(context: Context): android.content.SharedPreferences {
        val fresh = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!fresh.contains(KEY_SECRET)) {
            val old = context.getSharedPreferences(OLD_PREFS, Context.MODE_PRIVATE)
            if (old.contains(KEY_SECRET)) {
                fresh.edit()
                    .putString(KEY_SECRET, old.getString(KEY_SECRET, null))
                    .putBoolean(KEY_SET, old.getBoolean(KEY_SET, false))
                    .putString(KEY_ICON, old.getString(KEY_ICON, null))
                    .apply()
            }
        }
        return fresh
    }

    fun getSecret(context: Context): String? =
        prefs(context).getString(KEY_SECRET, null)

    fun isSecretSet(context: Context): Boolean {
        val p = prefs(context)
        return p.getBoolean(KEY_SET, false) && !p.getString(KEY_SECRET, null).isNullOrBlank()
    }

    fun setSecret(context: Context, secret: String) {
        prefs(context).edit()
            .putString(KEY_SECRET, secret.trim())
            .putBoolean(KEY_SET, true)
            .apply()
    }

    fun getThemeIcon(context: Context): String? =
        prefs(context).getString(KEY_ICON, null)

    fun setThemeIcon(context: Context, icon: String) =
        prefs(context).edit().putString(KEY_ICON, icon).apply()

    fun isDefault(context: Context): Boolean = !isSecretSet(context)

    fun matches(context: Context, expression: String): Boolean {
        val own = getSecret(context)
        if (!own.isNullOrBlank() && expression == own) return true
        if (!isSecretSet(context) && expression == DEFAULT_SECRET) return true
        return false
    }
}

sealed interface DeleteStatus {
    data object Deleted : DeleteStatus
    data class NeedsConfirmation(val sender: IntentSender) : DeleteStatus
    data object Failed : DeleteStatus
}

data class ImportResult(
    val blackHoleFile: File?,
    val deleteStatus: DeleteStatus?,
    val error: String? = null,
    val mediaUri: Uri? = null
)

object BlackHoleManager {

    // RENAME MIGRATION (2026-11-06): files used to live in
    // filesDir/vault/. Move the whole old folder to blackhole on
    // first use (or merge it in, if blackhole already exists), so
    // every photo / video / document / audio survives the rename.
    private fun blackHoleRoot(context: Context): File {
        val oldRoot = File(context.filesDir, "vault")
        val newRoot = File(context.filesDir, "blackhole")
        if (oldRoot.exists()) {
            if (!newRoot.exists()) {
                oldRoot.renameTo(newRoot)
            } else {
                oldRoot.listFiles()?.forEach { child ->
                    val dest = File(newRoot, child.name)
                    if (!dest.exists()) {
                        child.renameTo(dest)
                    } else if (child.isDirectory) {
                        child.listFiles()?.forEach { f -> f.renameTo(File(dest, f.name)) }
                        try { child.delete() } catch (_: Exception) {}
                    } else {
                        try { child.delete() } catch (_: Exception) {}
                    }
                }
                try { oldRoot.delete() } catch (_: Exception) {}
            }
        }
        return newRoot
    }

    fun blackHoleDir(context: Context, category: BlackHoleCategory): File {
        val root = blackHoleRoot(context)
        val dir = File(root, category.folder)
        if (!dir.exists()) dir.mkdirs()
        val nomedia = File(root, ".nomedia")
        if (!nomedia.exists()) try { nomedia.createNewFile() } catch (_: Exception) {}
        return dir
    }

    // OPENFIX 3: ".tmp" files and 0-byte files are NEVER listed.
    // Import writes to a .tmp file first (see importUri) and the
    // migration also leaves .tmp files behind if the app is killed
    // mid-migration — those used to show up in the grid as extra
    // "videos" that opened to nothing. A half-written import used
    // to appear the same way. Both are hidden now; only finished
    // files are shown.
    fun listFiles(context: Context, category: BlackHoleCategory): List<File> =
        blackHoleDir(context, category).listFiles()
            ?.filter { !it.name.endsWith(".tmp") && it.length() > 0 }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()

    fun fileSizeString(file: File): String {
        val bytes = file.length()
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        }
    }

    fun mimeFor(file: File): String = when (file.extension.lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png"         -> "image/png"
        "webp"        -> "image/webp"
        "gif"         -> "image/gif"
        "mp4"         -> "video/mp4"
        "mov"         -> "video/quicktime"
        "mkv"         -> "video/x-matroska"
        "webm"        -> "video/webm"
        "mp3"         -> "audio/mpeg"
        "wav"         -> "audio/wav"
        "m4a"         -> "audio/mp4"
        "aac"         -> "audio/aac"
        "ogg"         -> "audio/ogg"
        "pdf"         -> "application/pdf"
        "doc"         -> "application/msword"
        "docx"        -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xls"         -> "application/vnd.ms-excel"
        "xlsx"        -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "txt"         -> "text/plain"
        else          -> "application/octet-stream"
    }

    private fun sanitize(name: String) = name
        .map { if (it in "/\\:*?\"<>|") '_' else it }
        .joinToString("")
        .trim()
        .ifBlank { "file_${System.currentTimeMillis()}" }

    private fun uniqueFile(dir: File, originalName: String): File {
        val clean = sanitize(originalName)
        var candidate = File(dir, clean)
        var i = 1
        while (candidate.exists()) {
            val base = clean.substringBeforeLast(".", clean)
            val ext = if (clean.contains(".")) ".${clean.substringAfterLast(".")}" else ""
            candidate = File(dir, "${base}_$i$ext")
            i++
        }
        return candidate
    }

    private fun displayNameFromUri(context: Context, uri: Uri): String? = try {
        context.contentResolver.query(
            uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) cursor.getString(idx) else null
            } else null
        }
    } catch (_: Exception) { null }

    private fun resolveToMediaStoreUri(context: Context, pickerUri: Uri, category: BlackHoleCategory): Uri {
        val authority = pickerUri.authority ?: ""
        val path = pickerUri.path ?: ""

        if (authority == "media" && path.contains("picker")) {
            val id = pickerUri.lastPathSegment?.toLongOrNull()
            if (id != null) {
                return ContentUris.withAppendedId(mediaStoreCollection(category), id)
            }
        }
        if (authority == "media" && path.contains("/external/")) return pickerUri

        try {
            val docId = DocumentsContract.getDocumentId(pickerUri)
            if (docId.contains(":")) {
                val prefix = docId.substringBefore(":")
                val id = docId.substringAfter(":").toLongOrNull()
                if (id != null && prefix in listOf("image", "video", "audio")) {
                    return ContentUris.withAppendedId(mediaStoreCollection(category), id)
                }
            }
        } catch (_: Exception) {}

        pickerUri.lastPathSegment?.toLongOrNull()?.let { id ->
            if (authority == "media") {
                return ContentUris.withAppendedId(mediaStoreCollection(category), id)
            }
        }

        try {
            context.contentResolver.query(
                pickerUri,
                arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idIdx = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
                    if (idIdx >= 0) {
                        val id = cursor.getLong(idIdx)
                        if (id > 0) {
                            val collection = mediaStoreCollection(category)
                            return ContentUris.withAppendedId(collection, id)
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        val displayName = displayNameFromUri(context, pickerUri) ?: return pickerUri
        return findMediaStoreUriByName(context, displayName, category) ?: pickerUri
    }

    private fun mediaStoreCollection(category: BlackHoleCategory): Uri = when (category) {
        BlackHoleCategory.PHOTOS    -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        BlackHoleCategory.VIDEOS    -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        BlackHoleCategory.AUDIO     -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        BlackHoleCategory.DOCUMENTS -> MediaStore.Files.getContentUri("external")
    }

    private fun findMediaStoreUriByName(
        context: Context,
        displayName: String,
        category: BlackHoleCategory
    ): Uri? {
        val collection = mediaStoreCollection(category)
        return try {
            context.contentResolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
                arrayOf(displayName),
                "${MediaStore.MediaColumns.DATE_ADDED} DESC"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(0)
                    ContentUris.withAppendedId(collection, id)
                } else null
            }
        } catch (_: Exception) { null }
    }

    private fun deleteMediaStoreUri(context: Context, uri: Uri): DeleteStatus {
        return try {
            val deleted = context.contentResolver.delete(uri, null, null)
            if (deleted > 0) DeleteStatus.Deleted else DeleteStatus.Failed
        } catch (e: RecoverableSecurityException) {
            try {
                DeleteStatus.NeedsConfirmation(e.userAction.actionIntent.intentSender)
            } catch (_: Exception) { DeleteStatus.Failed }
        } catch (_: SecurityException) { DeleteStatus.Failed }
        catch (_: Exception) { DeleteStatus.Failed }
    }

    fun mediaUriExists(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.query(
                uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null
            )?.use { it.moveToFirst() } ?: false
        } catch (_: Exception) { false }
    }

    fun createDeleteRequestSender(context: Context, uri: Uri): IntentSender? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return try {
            val pending = MediaStore.createDeleteRequest(
                context.contentResolver, listOf(uri)
            )
            pending.intentSender
        } catch (_: Exception) { null }
    }

    // ── IMPORT (plain copy now — OPENFIX 4, no encryption) ────────
    // OPENFIX 3: (1) encrypts into "name.tmp" first and renames to
    // the real name only when the copy is COMPLETE — a half-written
    // import can no longer appear in the grid (listFiles hides
    // .tmp), so one upload can never show as two files with one
    // opening to nothing. (2) onProgress reports 0f..1f so the
    // Screen shows a real "Importing... 47%" bar.
    suspend fun importUri(
        context: Context,
        uri: Uri,
        category: BlackHoleCategory,
        onProgress: (Float) -> Unit = {}
    ): ImportResult =
        withContext(Dispatchers.IO) {
            val dir = blackHoleDir(context, category)
            val originalName = displayNameFromUri(context, uri)
                ?: "imported_${System.currentTimeMillis()}"
            val target = uniqueFile(dir, originalName)
            val tmp = File(dir, target.name + ".tmp")

            // Total size for the progress bar (-1 = unknown, then
            // the bar shows indeterminate instead of a wrong %).
            val total = try {
                context.contentResolver.openFileDescriptor(uri, "r")
                    ?.use { it.statSize } ?: -1L
            } catch (_: Exception) { -1L }

            // Step 1: OPENFIX 4 — PLAIN copy now, NO encryption.
            // The user removed encryption (2026-11-06): files only
            // need to stay hidden inside the app, not encrypted.
            // A plain copy opens instantly later. Progress still
            // reports so the "Importing... %" bar works.
            try {
                val input = context.contentResolver.openInputStream(uri)
                    ?: return@withContext ImportResult(null, null, "File khola jayni")
                input.use { src ->
                    FileOutputStream(tmp).use { dst ->
                        var written = 0L
                        val buf = ByteArray(65536)
                        while (true) {
                            val r = src.read(buf)
                            if (r == -1) break
                            dst.write(buf, 0, r)
                            written += r
                            if (total > 0) {
                                onProgress((written.toFloat() / total).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
                if (tmp.length() <= 0) {
                    try { tmp.delete() } catch (_: Exception) {}
                    return@withContext ImportResult(null, null, "Copy hoyni: khali file")
                }
                // Only now does the file become visible in the grid.
                if (!tmp.renameTo(target)) {
                    tmp.copyTo(target, overwrite = false)
                    tmp.delete()
                }
                onProgress(1f)
            } catch (e: Exception) {
                try { tmp.delete() } catch (_: Exception) {}
                return@withContext ImportResult(null, null, "Copy hoyni: ${e.message}")
            }

            // Step 2: original delete — আগের মতোই
            val mediaUri = resolveToMediaStoreUri(context, uri, category)
            var status = deleteMediaStoreUri(context, mediaUri)
            if (status is DeleteStatus.Failed) {
                val sender = createDeleteRequestSender(context, mediaUri)
                if (sender != null) {
                    status = DeleteStatus.NeedsConfirmation(sender)
                }
            }

            ImportResult(blackHoleFile = target, deleteStatus = status, mediaUri = mediaUri)
        }

    suspend fun retryDelete(context: Context, uri: Uri): Boolean =
        withContext(Dispatchers.IO) {
            try { context.contentResolver.delete(uri, null, null) > 0 }
            catch (_: Exception) { false }
        }

    // ── PHOTO PREVIEW ─────────────────────────────────────────────
    // BlackHoleScreen-এর পুরনো decodeSampled() এর বদলে এটা ব্যবহার করবে।
    // OPENFIX 4: plain files decode straight from the blackhole file
    // (see BlackHoleOpener.decodeSampledBitmap). Old encrypted files
    // still go through the streaming-decrypt temp file path.
    fun decodeSampledBitmap(file: File, reqSize: Int): Bitmap? {
        return BlackHoleOpener.decodeSampledBitmap(file, reqSize)
    }

    // ── ONE-TIME MIGRATION: old encrypted -> plain ───────────────
    // OPENFIX 4 (2026-11-06): the user removed encryption. This runs
    // once when the blackhole opens (LaunchedEffect(Unit) in the Screen)
    // and converts every still-encrypted file to plain, so it opens
    // instantly afterwards. Already-plain files are untouched.
    // How it tells them apart: it tries to decrypt each file once.
    // Decrypt succeeds = was encrypted (replace with the plain
    // copy). Decrypt throws (bad GCM tag) = already plain (leave it).
    // A false positive is cryptographically negligible (128-bit tag).
    // Returns how many files were converted.
    suspend fun migrateEncryptedFilesToPlain(context: Context): Int =
        withContext(Dispatchers.IO) {
            var migrated = 0
            for (cat in BlackHoleCategory.entries) {
                for (file in listFiles(context, cat)) {
                    val temp = File(file.parentFile, "${file.name}.tmp")
                    try {
                        FileInputStream(file).use { src ->
                            FileOutputStream(temp).use { dst ->
                                BlackHoleCrypto.decryptStream(src, dst)
                            }
                        }
                    } catch (_: Exception) {
                        // Already plain — leave it alone.
                        try { temp.delete() } catch (_: Exception) {}
                        continue
                    }
                    if (temp.length() <= 0) {
                        try { temp.delete() } catch (_: Exception) {}
                        continue
                    }
                    if (file.delete() && temp.renameTo(file)) migrated++
                    else try { temp.delete() } catch (_: Exception) {}
                }
            }
            migrated
        }

    // OPENFIX 4: copy a blackhole file OUT (Restore / Download).
    // Plain files copy as-is; old encrypted files are decrypted on
    // the way out. Used by restoreViaMediaStore / restoreViaLegacy.
    private fun copyOut(file: File, input: java.io.InputStream, output: java.io.OutputStream) {
        if (BlackHoleOpener.looksPlain(file)) {
            input.copyTo(output, 65536)
        } else {
            BlackHoleCrypto.decryptStream(input, output)
        }
    }

    // ── RESTORE (plain copy now, decrypt only for old files) ──────
    suspend fun restoreFile(context: Context, file: File, category: BlackHoleCategory): Boolean =
        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                restoreViaMediaStore(context, file, category)
            } else {
                restoreViaLegacy(context, file, category)
            }
        }

    // OPENFIX 2: Download = export a decrypted copy to the public
    // folder AND KEEP the blackhole file. Restore (above) deletes the
    // blackhole file after exporting; Download does not.
    suspend fun downloadFile(context: Context, file: File, category: BlackHoleCategory): Boolean =
        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                restoreViaMediaStore(context, file, category, deleteAfter = false)
            } else {
                restoreViaLegacy(context, file, category, deleteAfter = false)
            }
        }

    private fun restoreViaMediaStore(
        context: Context, file: File, category: BlackHoleCategory,
        deleteAfter: Boolean = true
    ): Boolean {
        return try {
            val (collection, relativePath) = when (category) {
                BlackHoleCategory.PHOTOS    -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI to
                        "${Environment.DIRECTORY_PICTURES}/CalNyx"
                BlackHoleCategory.VIDEOS    -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI to
                        "${Environment.DIRECTORY_MOVIES}/CalNyx"
                BlackHoleCategory.AUDIO     -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI to
                        "${Environment.DIRECTORY_MUSIC}/CalNyx"
                BlackHoleCategory.DOCUMENTS -> MediaStore.Downloads.EXTERNAL_CONTENT_URI to
                        "${Environment.DIRECTORY_DOWNLOADS}/CalNyx"
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeFor(file))
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            }
            val uri = context.contentResolver.insert(collection, values) ?: return false
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(file).use { src ->
                        copyOut(file, src, out)
                    }
                } ?: return false
            } catch (_: Exception) {
                // আধা লেখা restore মুছে দাও
                try { context.contentResolver.delete(uri, null, null) } catch (_: Exception) {}
                return false
            }
            if (deleteAfter) file.delete()
            true
        } catch (_: Exception) { false }
    }

    private suspend fun restoreViaLegacy(
        context: Context, file: File, category: BlackHoleCategory,
        deleteAfter: Boolean = true
    ): Boolean {
        return try {
            val publicDir = when (category) {
                BlackHoleCategory.PHOTOS    -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                BlackHoleCategory.VIDEOS    -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                BlackHoleCategory.AUDIO     -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                BlackHoleCategory.DOCUMENTS -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            }
            val destDir = File(publicDir, "CalNyx").also { it.mkdirs() }
            val dest = File(destDir, file.name)
            try {
                FileInputStream(file).use { src ->
                    FileOutputStream(dest).use { dst ->
                        copyOut(file, src, dst)
                    }
                }
            } catch (_: Exception) {
                try { dest.delete() } catch (_: Exception) {}
                return false
            }
            suspendCancellableCoroutine { cont ->
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(dest.absolutePath),
                    arrayOf(mimeFor(file))
                ) { _, _ -> cont.resume(Unit) }
            }
            if (deleteAfter) file.delete()
            true
        } catch (_: Exception) { false }
    }

    fun renameFile(file: File, newName: String): Boolean {
        val clean = sanitize(newName)
        if (clean.isBlank()) return false
        val withExt = if (clean.contains(".")) clean
        else {
            val ext = file.extension
            if (ext.isNotBlank()) "$clean.$ext" else clean
        }
        val target = File(file.parentFile, withExt)
        if (target.exists()) return false
        return file.renameTo(target)
    }

    fun deleteFile(file: File): Boolean = try { file.delete() } catch (_: Exception) { false }

    fun clearPreviewCache(context: Context) {
        try {
            File(context.cacheDir, "blackhole_preview").deleteRecursively()
        } catch (_: Exception) {}
        try {
            File(context.cacheDir, "vault_preview").deleteRecursively()
        } catch (_: Exception) {}
    }

    // ── OPEN (video / audio / document) ───────────────────────────
    // OPENFIX 4: plain files open instantly — BlackHoleOpener hands the
    // blackhole file itself to the player via FileProvider. Old encrypted
    // files still go through the decrypt-preview path (once, until
    // the one-time migration converts them to plain).
    // OPENFIX 3: onProgress reports 0f..1f of the decrypt, so the
    // Screen shows a real "Opening... 47%" bar.
    suspend fun openWithExternalViewer(
        context: Context,
        file: File,
        onProgress: (Float) -> Unit = {}
    ): Boolean {
        return BlackHoleOpener.openExternal(context, file, onProgress) is BlackHoleOpenResult.Opened
    }
}

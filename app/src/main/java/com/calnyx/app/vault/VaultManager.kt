// FILE: app/src/main/java/com/calnyx/app/vault/VaultManager.kt
// OPENFIX VERSION - replace your old VaultManager.kt with this file.
// Based on ENCRYPTED. Only 2 functions changed (see VaultOpener.kt):
// decodeSampledBitmap now streams via VaultOpener (no RAM load),
// openWithExternalViewer is now SUSPEND and delegates to VaultOpener.
// Keep the file name VaultManager.kt, do not keep both files,
// otherwise you will get a duplicate class error.
// Also add the new file VaultCrypto.kt in the same folder.
//
// What changed vs your old file (everything else is unchanged):
// 1. importUri: copies the picked file ENCRYPTED, not plain.
// 2. restoreFile: decrypts while restoring to the CalNyx folder.
// 3. openWithExternalViewer: decrypts to app cache (vault_preview),
//    opens that, never opens the encrypted file directly. The cache
//    file is cleared on the next open, and it is never in gallery.
// 4. New helpers: decodeSampledBitmap, migratePlainFilesToEncrypted,
//    clearPreviewCache.
// 5. Delete / resolve / MediaStore logic is exactly as before.
package com.calnyx.app.vault

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

enum class VaultCategory(val folder: String, val title: String, val pickerMime: String) {
    PHOTOS("photos", "Photos", "image/*"),
    VIDEOS("videos", "Videos", "video/*"),
    DOCUMENTS("documents", "Documents", "*/*"),
    AUDIO("audio", "Audio", "audio/*")
}

object VaultPrefs {
    private const val PREFS = "vault_prefs"
    private const val KEY_SECRET = "vault_secret"
    private const val KEY_SET = "vault_secret_set"
    private const val KEY_ICON = "theme_icon"

    const val DEFAULT_SECRET = "112233"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

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
    val vaultFile: File?,
    val deleteStatus: DeleteStatus?,
    val error: String? = null,
    val mediaUri: Uri? = null
)

object VaultManager {

    fun vaultDir(context: Context, category: VaultCategory): File {
        val root = File(context.filesDir, "vault")
        val dir = File(root, category.folder)
        if (!dir.exists()) dir.mkdirs()
        val nomedia = File(root, ".nomedia")
        if (!nomedia.exists()) try { nomedia.createNewFile() } catch (_: Exception) {}
        return dir
    }

    fun listFiles(context: Context, category: VaultCategory): List<File> =
        vaultDir(context, category).listFiles()
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

    private fun resolveToMediaStoreUri(context: Context, pickerUri: Uri, category: VaultCategory): Uri {
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

    private fun mediaStoreCollection(category: VaultCategory): Uri = when (category) {
        VaultCategory.PHOTOS    -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        VaultCategory.VIDEOS    -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        VaultCategory.AUDIO     -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        VaultCategory.DOCUMENTS -> MediaStore.Files.getContentUri("external")
    }

    private fun findMediaStoreUriByName(
        context: Context,
        displayName: String,
        category: VaultCategory
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

    // ── ENCRYPTED IMPORT ──────────────────────────────────────────
    suspend fun importUri(context: Context, uri: Uri, category: VaultCategory): ImportResult =
        withContext(Dispatchers.IO) {
            val dir = vaultDir(context, category)
            val originalName = displayNameFromUri(context, uri)
                ?: "imported_${System.currentTimeMillis()}"
            val target = uniqueFile(dir, originalName)

            // Step 1: vault-এ ENCRYPT করে copy করো (আগে plain copy ছিল)
            try {
                val input = context.contentResolver.openInputStream(uri)
                    ?: return@withContext ImportResult(null, null, "File khola jayni")
                input.use { src ->
                    FileOutputStream(target).use { dst ->
                        VaultCrypto.encryptStream(src, dst)
                    }
                }
            } catch (e: Exception) {
                try { target.delete() } catch (_: Exception) {}
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

            ImportResult(vaultFile = target, deleteStatus = status, mediaUri = mediaUri)
        }

    suspend fun retryDelete(context: Context, uri: Uri): Boolean =
        withContext(Dispatchers.IO) {
            try { context.contentResolver.delete(uri, null, null) > 0 }
            catch (_: Exception) { false }
        }

    // ── ENCRYPTED PHOTO PREVIEW (memory only) ─────────────────────
    // VaultScreen-এর পুরনো decodeSampled() এর বদলে এটা ব্যবহার করবে।
    // ফাইলটা ডিক্রিপ্ট করে memory-তে Bitmap বানায়, ফোনে কোনো
    // plain ফাইল তৈরি করে না।
    // OPENFIX: streaming thumbnail — decrypts to a temp FILE and
    // decodes sampled FROM the file. RAM is only the final bitmap.
    // The old version loaded the WHOLE file into RAM twice
    // (decryptToBytes + readBytes) — OOM on large photos / low RAM.
    fun decodeSampledBitmap(file: File, reqSize: Int): Bitmap? {
        return VaultOpener.decodeSampledBitmap(file, reqSize)
    }

    // ── ONE-TIME MIGRATION: পুরনো plain -> encrypted ─────────────
    // VaultScreen খোলার সময় একবার চালাও (patch ফাইলে দেওয়া আছে)।
    // Already encrypted ফাইল ছোঁয়া হবে না, শুধু plain ফাইল
    // এনক্রিপ্ট হবে। ফেরত দেয় কয়টা ফাইল migrate হলো।
    suspend fun migratePlainFilesToEncrypted(context: Context): Int =
        withContext(Dispatchers.IO) {
            var migrated = 0
            for (cat in VaultCategory.entries) {
                for (file in listFiles(context, cat)) {
                    val temp = File(file.parentFile, "${file.name}.tmp")
                    // 1. Decrypt চেষ্টা করো — সফল হলে already encrypted
                    val alreadyEncrypted = try {
                        FileInputStream(file).use { src ->
                            FileOutputStream(temp).use { dst ->
                                VaultCrypto.decryptStream(src, dst)
                            }
                        }
                        true
                    } catch (_: Exception) { false }
                    try { temp.delete() } catch (_: Exception) {}
                    if (alreadyEncrypted) continue
                    // 2. Plain হলে encrypt করে replace করো
                    try {
                        FileInputStream(file).use { src ->
                            FileOutputStream(temp).use { dst ->
                                VaultCrypto.encryptStream(src, dst)
                            }
                        }
                        if (file.delete() && temp.renameTo(file)) migrated++
                        else try { temp.delete() } catch (_: Exception) {}
                    } catch (_: Exception) {
                        try { temp.delete() } catch (_: Exception) {}
                    }
                }
            }
            migrated
        }

    // ── ENCRYPTED RESTORE ─────────────────────────────────────────
    suspend fun restoreFile(context: Context, file: File, category: VaultCategory): Boolean =
        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                restoreViaMediaStore(context, file, category)
            } else {
                restoreViaLegacy(context, file, category)
            }
        }

    private fun restoreViaMediaStore(
        context: Context, file: File, category: VaultCategory
    ): Boolean {
        return try {
            val (collection, relativePath) = when (category) {
                VaultCategory.PHOTOS    -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI to
                        "${Environment.DIRECTORY_PICTURES}/CalNyx"
                VaultCategory.VIDEOS    -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI to
                        "${Environment.DIRECTORY_MOVIES}/CalNyx"
                VaultCategory.AUDIO     -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI to
                        "${Environment.DIRECTORY_MUSIC}/CalNyx"
                VaultCategory.DOCUMENTS -> MediaStore.Downloads.EXTERNAL_CONTENT_URI to
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
                        VaultCrypto.decryptStream(src, out)
                    }
                } ?: return false
            } catch (_: Exception) {
                // আধা লেখা restore মুছে দাও
                try { context.contentResolver.delete(uri, null, null) } catch (_: Exception) {}
                return false
            }
            file.delete()
            true
        } catch (_: Exception) { false }
    }

    private suspend fun restoreViaLegacy(
        context: Context, file: File, category: VaultCategory
    ): Boolean {
        return try {
            val publicDir = when (category) {
                VaultCategory.PHOTOS    -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                VaultCategory.VIDEOS    -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                VaultCategory.AUDIO     -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                VaultCategory.DOCUMENTS -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            }
            val destDir = File(publicDir, "CalNyx").also { it.mkdirs() }
            val dest = File(destDir, file.name)
            try {
                FileInputStream(file).use { src ->
                    FileOutputStream(dest).use { dst ->
                        VaultCrypto.decryptStream(src, dst)
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
            file.delete()
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
            File(context.cacheDir, "vault_preview").deleteRecursively()
        } catch (_: Exception) {}
    }

    // ── ENCRYPTED OPEN (video / audio / document) ─────────────────
    // Encrypted ফাইল সরাসরি FileProvider দিয়ে খোলা যাবে না, তাই
    // cache-এ ডিক্রিপ্ট করে সেটা খোলা হয়। Cache ফোনের internal
    // জায়গা, gallery / Files-এ দেখা যাবে না। পরের বার খুললে
    // আগের preview মুছে যাবে।
    // OPENFIX: SUSPEND now — decrypt/copy runs on Dispatchers.IO
    // inside VaultOpener, NEVER on the main thread. The old version
    // ran here synchronously: a large video blocked the main thread
    // for 10-60s = ANR = Android killed the app. It also deleted ALL
    // previews first, breaking a player still reading one.
    suspend fun openWithExternalViewer(context: Context, file: File): Boolean {
        return VaultOpener.openExternal(context, file) is VaultOpenResult.Opened
    }
}

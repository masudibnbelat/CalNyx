// FILE: app/src/main/java/com/calnyx/app/vault/VaultManager.kt
package com.calnyx.app.vault

import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentSender
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
    // FIX: import-এর সময় resolve করা আসল MediaStore URI.
    // সিস্টেম পারমিশন এই URI-র জন্য দেওয়া হয়, picker URI-র জন্য না।
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

    /**
     * picker URI (content://com.android.providers.media.documents/...)
     * থেকে real MediaStore URI বের করে।
     *
     * Strategy:
     * 1. URI তে "_id" column query করার চেষ্টা — MediaStore URI হলে সরাসরি পাওয়া যাবে
     * 2. Display name দিয়ে MediaStore এ search করবো
     * 3. দুটোই fail করলে original URI return
     */
    private fun resolveToMediaStoreUri(context: Context, pickerUri: Uri, category: VaultCategory): Uri {
        val authority = pickerUri.authority ?: ""
        val path = pickerUri.path ?: ""

        // FIX3: স্ক্রিনশটের আসল URI:
        // content://media/picker_get_content/0/com.android.providers.media.photopicker/media/<id>
        // আগের v2 শুধু "/picker/" খুঁজত, কিন্তু এখানে "picker_get_content"
        // আছে, তাই v2 ওই চেক মিস করেছে। picker / picker_get_content —
        // দুটোতেই শেষের segment-ই আসল MediaStore id।
        // এটা সবার আগে চেক করতে হবে, query-র _ID-র আগে, কারণ
        // picker provider-এর query ভুল id দিতে পারে।
        if (authority == "media" && path.contains("picker")) {
            val id = pickerUri.lastPathSegment?.toLongOrNull()
            if (id != null) {
                return ContentUris.withAppendedId(mediaStoreCollection(category), id)
            }
        }
        // Already a direct MediaStore URI? content://media/external/.../media/<id>
        if (authority == "media" && path.contains("/external/")) return pickerUri

        // FIX: DocumentsProvider URI হলে (যেমন
        // com.android.providers.media.documents/document/image:123)
        // documentId থেকে সরাসরি MediaStore id বের করো।
        // এটা query / নাম দিয়ে খোঁজার চেয়ে নির্ভরযোগ্য।
        // শুধু image:/video:/audio: prefix-এর id-ই ব্যবহার করো,
        // msf: বা primary: পাথের ক্ষেত্রে নাম দিয়ে খোঁজা ভালো।
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

        // FIX3 safety net: শেষ segment numeric হলে সেটাই MediaStore id
        // (picker_get_content ছাড়া অন্য picker-এর ক্ষেত্রেও কাজ করবে)।
        pickerUri.lastPathSegment?.toLongOrNull()?.let { id ->
            if (authority == "media") {
                return ContentUris.withAppendedId(mediaStoreCollection(category), id)
            }
        }

        // Try to get _id directly from the picker URI
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

        // Fallback: search by display name in MediaStore
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
            // Android 11+ — video/audio needs user confirmation dialog
            try {
                DeleteStatus.NeedsConfirmation(e.userAction.actionIntent.intentSender)
            } catch (_: Exception) { DeleteStatus.Failed }
        } catch (_: SecurityException) { DeleteStatus.Failed }
        catch (_: Exception) { DeleteStatus.Failed }
    }

    /** FIX2: এই MediaStore URI-টা এখনো আছে কিনা। */
    fun mediaUriExists(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.query(
                uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null
            )?.use { it.moveToFirst() } ?: false
        } catch (_: Exception) { false }
    }

    /** FIX2: Android 11+ এর অফিসিয়াল delete request-এর sender, বা null। */
    fun createDeleteRequestSender(context: Context, uri: Uri): IntentSender? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return try {
            val pending = MediaStore.createDeleteRequest(
                context.contentResolver, listOf(uri)
            )
            pending.intentSender
        } catch (_: Exception) { null }
    }

    suspend fun importUri(context: Context, uri: Uri, category: VaultCategory): ImportResult =
        withContext(Dispatchers.IO) {
            val dir = vaultDir(context, category)
            val originalName = displayNameFromUri(context, uri)
                ?: "imported_${System.currentTimeMillis()}"
            val target = uniqueFile(dir, originalName)

            // Step 1: vault এ copy করো
            try {
                val input = context.contentResolver.openInputStream(uri)
                    ?: return@withContext ImportResult(null, null, "File khola jayni")
                input.use { src -> FileOutputStream(target).use { dst -> src.copyTo(dst) } }
            } catch (e: Exception) {
                try { target.delete() } catch (_: Exception) {}
                return@withContext ImportResult(null, null, "Copy hoyni: ${e.message}")
            }

            // Step 2: original delete — real MediaStore URI resolve করে
            val mediaUri = resolveToMediaStoreUri(context, uri, category)
            var status = deleteMediaStoreUri(context, mediaUri)
            // FIX2: সরাসরি delete Failed হলে (Recoverable-ও আসেনি),
            // অফিসিয়াল createDeleteRequest দিয়ে সিস্টেম ডায়ালগের
            // আরেকটা সুযোগ নাও — এটাই স্ক্রিনশটের Failed কেসের ফিক্স।
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
            context.contentResolver.openOutputStream(uri)?.use { out ->
                FileInputStream(file).use { src -> src.copyTo(out) }
            } ?: return false
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
            FileInputStream(file).use { src ->
                FileOutputStream(dest).use { dst -> src.copyTo(dst) }
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

    fun openWithExternalViewer(context: Context, file: File): Boolean {
        return try {
            val uri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeFor(file))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Open with"))
            true
        } catch (_: Exception) { false }
    }
}

// FILE: app/src/main/java/com/calnyx/app/vault/VaultManager.kt
package com.calnyx.app.vault

import android.app.RecoverableSecurityException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Vault folders live in the app's INTERNAL storage: filesDir/vault/photos, videos, documents, audio
 * Other apps and the gallery cannot see internal storage, and MediaStore never indexes it. No extra
 * permission is needed to read/write here. Warning: uninstalling the app deletes the vault, so
 * restore important files before uninstalling.
 *
 * v1 is HIDDEN, not file-by-file encrypted. On a non-rooted phone only this app can read these
 * files, which is what Calculator Vault apps do. Encryption is a separate next step if you want it.
 */
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

    /**
     * Old fallback, kept ONLY for users who already opened the vault with it before they set their
     * own secret. Once a user sets their own secret, this stops working. New users must NOT need to
     * know this — they set their own secret from the menu setup screen, or by long-pressing "=" in
     * the Normal calculator while no secret is set.
     */
    const val DEFAULT_SECRET = "112233"

    /** Null when the user has not set their own secret yet. */
    fun getSecret(context: Context): String? {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SECRET, null)
    }

    /** True only after the user has saved their own secret. */
    fun isSecretSet(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SET, false) &&
                !prefs.getString(KEY_SECRET, null).isNullOrBlank()
    }

    fun setSecret(context: Context, secret: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_SECRET, secret.trim())
                .putBoolean(KEY_SET, true)
                .apply()
    }

    /** Old name, kept for VaultScreen: true = own secret not set yet. */
    fun isDefault(context: Context): Boolean = !isSecretSet(context)

    /**
     * Does this expression open the vault? Own secret always works. The old default works only
     * until the user sets their own secret, so existing vault files are not locked out by an
     * update.
     */
    fun matches(context: Context, expression: String): Boolean {
        val own = getSecret(context)
        if (!own.isNullOrBlank() && expression == own) return true
        if (!isSecretSet(context) && expression == DEFAULT_SECRET) return true
        return false
    }
}

sealed interface DeleteStatus {
    /** Original was deleted from shared storage. */
    data object Deleted : DeleteStatus

    /** Android needs the user to confirm this delete in a system dialog. */
    data class NeedsConfirmation(val sender: IntentSender) : DeleteStatus

    /** Copy succeeded, but the original could not be deleted. */
    data object Failed : DeleteStatus
}

data class ImportResult(
        val vaultFile: File?,
        val deleteStatus: DeleteStatus?,
        val error: String? = null
)

object VaultManager {

    fun vaultDir(context: Context, category: VaultCategory): File {
        val root = File(context.filesDir, "vault")
        val dir = File(root, category.folder)
        if (!dir.exists()) dir.mkdirs()
        // Extra safety: even if a scanner ever looked here, skip it.
        val nomedia = File(root, ".nomedia")
        if (!nomedia.exists()) {
            try {
                nomedia.createNewFile()
            } catch (_: Exception) {}
        }
        return dir
    }

    fun listFiles(context: Context, category: VaultCategory): List<File> {
        val dir = vaultDir(context, category)
        return dir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    fun fileSizeString(file: File): String {
        val bytes = file.length()
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        }
    }

    fun mimeFor(file: File): String {
        return when (file.extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "mp4" -> "video/mp4"
            "mov" -> "video/quicktime"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "m4a" -> "audio/mp4"
            "aac" -> "audio/aac"
            "ogg" -> "audio/ogg"
            "pdf" -> "application/pdf"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "txt" -> "text/plain"
            else -> "application/octet-stream"
        }
    }

    private fun sanitize(name: String): String {
        return name
                .map { ch -> if (ch in "/\\:*?\"<>|") '_' else ch }
                .joinToString("")
                .trim()
                .ifBlank { "file_${System.currentTimeMillis()}" }
    }

    private fun uniqueFile(dir: File, originalName: String): File {
        val clean = sanitize(originalName)
        var candidate = File(dir, clean)
        var i = 1
        while (candidate.exists()) {
            val base = clean.substringBeforeLast(".", clean)
            val ext = if (clean.contains(".")) "." + clean.substringAfterLast(".") else ""
            candidate = File(dir, "${base}_$i$ext")
            i++
        }
        return candidate
    }

    private fun displayNameFromUri(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(
                            uri,
                            arrayOf(OpenableColumns.DISPLAY_NAME),
                            null,
                            null,
                            null
                    )
                    ?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (idx >= 0) cursor.getString(idx) else null
                        } else null
                    }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Copy first, then try to delete the original. Never delete before the copy is safely on disk.
     * Returns NeedsConfirmation on Android 11+ when the system must ask the user — the caller
     * launches that IntentSender.
     */
    suspend fun importUri(context: Context, uri: Uri, category: VaultCategory): ImportResult =
            withContext(Dispatchers.IO) {
                val dir = vaultDir(context, category)
                val originalName =
                        displayNameFromUri(context, uri) ?: "imported_${System.currentTimeMillis()}"
                val target = uniqueFile(dir, originalName)
                try {
                    val input =
                            context.contentResolver.openInputStream(uri)
                                    ?: return@withContext ImportResult(
                                            null,
                                            null,
                                            "File khola jayni"
                                    )
                    input.use { src -> FileOutputStream(target).use { dst -> src.copyTo(dst) } }
                } catch (e: Exception) {
                    try {
                        target.delete()
                    } catch (_: Exception) {}
                    return@withContext ImportResult(null, null, "Copy hoyni: ${e.message}")
                }

                // Copy is safe now — try to remove the original.
                val status: DeleteStatus =
                        try {
                            val deleted = context.contentResolver.delete(uri, null, null)
                            if (deleted > 0) DeleteStatus.Deleted else DeleteStatus.Failed
                        } catch (e: RecoverableSecurityException) {
                            try {
                                val sender = e.userAction.actionIntent.intentSender
                                DeleteStatus.NeedsConfirmation(sender)
                            } catch (_: Exception) {
                                DeleteStatus.Failed
                            }
                        } catch (_: SecurityException) {
                            DeleteStatus.Failed
                        } catch (_: Exception) {
                            DeleteStatus.Failed
                        }
                ImportResult(target, status, null)
            }

    /** Retry a delete after the user confirmed it in the system dialog. */
    suspend fun retryDelete(context: Context, uri: Uri): Boolean =
            withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.delete(uri, null, null) > 0
                } catch (_: Exception) {
                    false
                }
            }

    /**
     * Restore: write the vault file back to public storage through MediaStore (so it appears in
     * Gallery / Files again), then delete it from the vault. Android 10+ only in this version.
     */
    suspend fun restoreFile(context: Context, file: File, category: VaultCategory): Boolean =
            withContext(Dispatchers.IO) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext false
                try {
                    val (collection, relativePath) =
                            when (category) {
                                VaultCategory.PHOTOS ->
                                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI to
                                                "${Environment.DIRECTORY_PICTURES}/CalNyx"
                                VaultCategory.VIDEOS ->
                                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI to
                                                "${Environment.DIRECTORY_MOVIES}/CalNyx"
                                VaultCategory.AUDIO ->
                                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI to
                                                "${Environment.DIRECTORY_MUSIC}/CalNyx"
                                VaultCategory.DOCUMENTS ->
                                        MediaStore.Downloads.EXTERNAL_CONTENT_URI to
                                                "${Environment.DIRECTORY_DOWNLOADS}/CalNyx"
                            }
                    val values =
                            ContentValues().apply {
                                put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                                put(MediaStore.MediaColumns.MIME_TYPE, mimeFor(file))
                                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                            }
                    val uri =
                            context.contentResolver.insert(collection, values)
                                    ?: return@withContext false
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        FileInputStream(file).use { src -> src.copyTo(out) }
                    }
                            ?: return@withContext false
                    file.delete()
                    true
                } catch (_: Exception) {
                    false
                }
            }

    /** Rename inside the vault. A missing extension keeps the old one. */
    fun renameFile(file: File, newName: String): Boolean {
        val clean = sanitize(newName)
        if (clean.isBlank()) return false
        val withExt =
                if (clean.contains(".")) clean
                else {
                    val ext = file.extension
                    if (ext.isNotBlank()) "$clean.$ext" else clean
                }
        val target = File(file.parentFile, withExt)
        if (target.exists()) return false
        return file.renameTo(target)
    }

    fun deleteFile(file: File): Boolean {
        return try {
            file.delete()
        } catch (_: Exception) {
            false
        }
    }

    /** Open a vault file in an external viewer via FileProvider. */
    fun openWithExternalViewer(context: Context, file: File): Boolean {
        return try {
            val uri =
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent =
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, mimeFor(file))
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
            context.startActivity(Intent.createChooser(intent, "Open with"))
            true
        } catch (_: Exception) {
            false
        }
    }
}

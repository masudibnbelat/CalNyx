// FILE: app/src/main/java/com/calnyx/app/vault/VaultOpener.kt
// NEW FILE - Vault open fix (2026-11-06). Add this file, do NOT
// replace VaultManager.kt with it. Then apply VaultManager-PATCH.txt
// (2 functions) and VaultScreen-PATCH.txt (tap handling) from the
// same folder.
//
// WHY THE OLD CODE CRASHED / FROZE (read before doubting the fix):
// 1. VaultManager.openWithExternalViewer ran ON THE MAIN THREAD:
//    it decrypted the WHOLE file to cache before showing the
//    chooser. A large video = 10-60s main-thread block = ANR,
//    Android kills the app. That is the "open hote deri, app bondho".
// 2. decodeSampledBitmap loaded the WHOLE decrypted file into RAM
//    (VaultCrypto.decryptToBytes + file.readBytes) and decoded it
//    twice. A 12-20MP photo = tens of MB in RAM = OOM / freeze on
//    low-RAM ("lighter") phones. Even a 256px thumbnail did this.
// 3. A photo tap opened an in-app Dialog viewer (2 taps), not an
//    external viewer like Blue Film does.
//
// WHAT THIS FILE DOES:
// - openExternal: streaming decrypt/copy on Dispatchers.IO (8KB
//   buffer, never whole file in RAM), instant "Opening..." is shown
//   by the Screen while this runs, chooser appears when ready.
//   A finished preview is REUSED on re-open (length-checked), so
//   the 2nd open of the same file is instant.
// - decodeSampledBitmap: streaming decrypt to a temp FILE, then
//   BitmapFactory decodes bounds + sampled from that FILE. RAM
//   use is only the sampled bitmap. No decryptToBytes, no readBytes.
// - MIME table is wider than the old mimeFor (heic/avif/3gp/m4v/
//   flac/opus/epub...), fallback is "*/*" — NEVER
//   "application/octet-stream" as the intent type, players reject it.
// - Works Android 9 (API 28) to 18: only APIs available since API 28
//   are used, with a version guard for queryIntentActivities flags.
// - No new library. Same package as VaultManager/VaultCrypto, so no
//   import is needed for them.
//
// HONEST LIMIT (do not remove): an encrypted video CANNOT be
// streamed-decrypted to an external player — the player needs a
// real decrypted file. So the FIRST open of a large video still
// needs a decrypt-copy to cache. The difference now: it runs on a
// background thread with progress shown, so the app stays alive
// and never ANRs. Re-opening the same file reuses the preview.
package com.calnyx.app.vault

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URLConnection

sealed interface VaultOpenResult {
    object Opened : VaultOpenResult
    object NoViewer : VaultOpenResult
    data class Failed(val msg: String) : VaultOpenResult
}

object VaultOpener {

    private const val BUFFER_SIZE = 8192 // 8KB streaming buffer, never whole file in RAM.

    // ── EXTERNAL OPEN (video / photo / audio / document) ──────────
    // Suspend: call from scope.launch in the Screen. The decrypt/copy
    // runs on Dispatchers.IO; only startActivity runs on Main.
    suspend fun openExternal(context: Context, file: File): VaultOpenResult {
        val preview = try {
            preparePreview(context, file)
        } catch (e: Exception) {
            return VaultOpenResult.Failed(e.message ?: "Could not prepare file")
        }
        if (preview == null) return VaultOpenResult.Failed("Could not prepare file")

        val mime = mimeForOpen(file)
        val uri: Uri = try {
            FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", preview
            )
        } catch (e: Exception) {
            return VaultOpenResult.Failed("FileProvider: ${e.message}")
        }

        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        // If no app handles this MIME, retry with */* before giving up.
        val resolved = resolveActivities(context, viewIntent)
        val finalIntent: Intent
        val finalMime: String
        if (resolved.isNotEmpty()) {
            finalIntent = viewIntent
            finalMime = mime
        } else {
            val anyIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (resolveActivities(context, anyIntent).isEmpty()) {
                return VaultOpenResult.NoViewer
            }
            finalIntent = anyIntent
            finalMime = "*/*"
        }
        // Some OEMs (Android 9-12) ignore the FLAG on the chooser, so
        // grant the URI explicitly to every app that can open it.
        // (finalMime is only kept for clarity; the grant is per-URI.)
        @Suppress("UNUSED_VARIABLE")
        val usedMime = finalMime
        return try {
            withContext(Dispatchers.Main) {
                resolveActivities(context, finalIntent).forEach { info ->
                    try {
                        context.grantUriPermission(
                            info.activityInfo.packageName, uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (_: Exception) {
                    }
                }
                context.startActivity(Intent.createChooser(finalIntent, "Open with"))
            }
            VaultOpenResult.Opened
        } catch (e: SecurityException) {
            VaultOpenResult.Failed("No permission to share file")
        } catch (e: Exception) {
            VaultOpenResult.Failed(e.message ?: "Could not open file")
        }
    }

    // Decrypt (or plain-copy) the vault file to cache/vault_preview
    // ONCE, then reuse it. Runs on Dispatchers.IO. Returns null only
    // if neither decrypt nor plain copy produced a usable file.
    private suspend fun preparePreview(context: Context, file: File): File? =
        withContext(Dispatchers.IO) {
            val previewDir = File(context.cacheDir, "vault_preview").also { it.mkdirs() }
            val preview = File(previewDir, file.name)
            // Reuse check: a finished preview has exactly the expected
            // length — encrypted: file minus 12-byte IV minus 16-byte
            // GCM tag (28); plain: same length as the vault file.
            // A half-written preview from a killed earlier open fails
            // this check and is re-decrypted instead of played broken.
            if (preview.exists() && preview.length() > 0) {
                val expectedEncrypted = file.length() - 28
                if (preview.length() == expectedEncrypted || preview.length() == file.length()) {
                    return@withContext preview
                } else {
                    try { preview.delete() } catch (_: Exception) {}
                }
            }
            val tmp = File(previewDir, "${file.name}.tmp")
            try {
                try {
                    FileInputStream(file).use { src ->
                        FileOutputStream(tmp).use { dst ->
                            VaultCrypto.decryptStream(src, dst)
                        }
                    }
                } catch (_: Exception) {
                    // Old plain file (before encryption): stream-copy it.
                    try { tmp.delete() } catch (_: Exception) {}
                    FileInputStream(file).use { src ->
                        FileOutputStream(tmp).use { dst ->
                            src.copyTo(dst, BUFFER_SIZE)
                        }
                    }
                }
                if (tmp.length() <= 0) {
                    try { tmp.delete() } catch (_: Exception) {}
                    return@withContext null
                }
                if (!tmp.renameTo(preview)) {
                    // rename failed (same name open twice): use tmp as-is.
                    return@withContext tmp
                }
                preview
            } catch (_: Exception) {
                try { tmp.delete() } catch (_: Exception) {}
                null
            }
        }

    private fun resolveActivities(context: Context, intent: Intent): List<android.content.pm.ResolveInfo> {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.queryIntentActivities(
                    intent,
                    PackageManager.ResolveInfoFlags.of(
                        PackageManager.MATCH_DEFAULT_ONLY.toLong()
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.queryIntentActivities(
                    intent, PackageManager.MATCH_DEFAULT_ONLY
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    // Wider MIME table than the old VaultManager.mimeFor. Fallback is
    // "*/*" so the chooser still appears for unknown extensions.
    fun mimeForOpen(file: File): String {
        when (file.extension.lowercase()) {
            "jpg", "jpeg" -> return "image/jpeg"
            "png" -> return "image/png"
            "webp" -> return "image/webp"
            "gif" -> return "image/gif"
            "heic", "heif" -> return "image/heic"
            "avif" -> return "image/avif"
            "bmp" -> return "image/bmp"
            "mp4" -> return "video/mp4"
            "m4v" -> return "video/mp4"
            "mov" -> return "video/quicktime"
            "mkv" -> return "video/x-matroska"
            "webm" -> return "video/webm"
            "3gp" -> return "video/3gpp"
            "avi" -> return "video/x-msvideo"
            "mp3" -> return "audio/mpeg"
            "wav" -> return "audio/wav"
            "m4a" -> return "audio/mp4"
            "aac" -> return "audio/aac"
            "ogg" -> return "audio/ogg"
            "flac" -> return "audio/flac"
            "opus" -> return "audio/opus"
            "mid", "midi" -> return "audio/midi"
            "pdf" -> return "application/pdf"
            "doc" -> return "application/msword"
            "docx" -> return "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> return "application/vnd.ms-excel"
            "xlsx" -> return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "ppt" -> return "application/vnd.ms-powerpoint"
            "pptx" -> return "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "epub" -> return "application/epub+zip"
            "txt" -> return "text/plain"
        }
        return URLConnection.guessContentTypeFromName(file.name) ?: "*/*"
    }

    // ── STREAMING THUMBNAIL (photos) ──────────────────────────────
    // BLOCKING file IO — call only from Dispatchers.IO (the Screen
    // already does that in VaultPhotoThumbnail / VaultPhotoImage).
    // Decrypts streaming into a temp FILE, decodes bounds + sampled
    // bitmap FROM THAT FILE, deletes the temp file. RAM use is only
    // the final sampled bitmap. No decryptToBytes, no readBytes.
    fun decodeSampledBitmap(file: File, reqSize: Int): Bitmap? {
        var tmp: File? = null
        return try {
            tmp = File.createTempFile("vault_thumb", ".tmp")
            try {
                FileInputStream(file).use { src ->
                    FileOutputStream(tmp).use { dst ->
                        VaultCrypto.decryptStream(src, dst)
                    }
                }
            } catch (_: Exception) {
                // Old plain file: stream-copy it instead.
                try { tmp.delete() } catch (_: Exception) {}
                tmp = File.createTempFile("vault_thumb", ".tmp")
                FileInputStream(file).use { src ->
                    FileOutputStream(tmp).use { dst ->
                        src.copyTo(dst, BUFFER_SIZE)
                    }
                }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(tmp.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (bounds.outWidth / sample > reqSize ||
                bounds.outHeight / sample > reqSize
            ) sample *= 2
            BitmapFactory.decodeFile(
                tmp.absolutePath,
                BitmapFactory.Options().apply { inSampleSize = sample }
            )
        } catch (_: Exception) {
            null
        } finally {
            try { tmp?.delete() } catch (_: Exception) {}
        }
    }
}

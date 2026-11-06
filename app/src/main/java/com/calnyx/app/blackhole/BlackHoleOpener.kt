// FILE: app/src/main/java/com/calnyx/app/blackhole/BlackHoleOpener.kt
// OPENFIX 4 VERSION (2026-11-06). Add this file, do NOT replace
// BlackHoleManager.kt with it.
//
// ENCRYPTION REMOVED by the user's choice: blackhole files are stored
// PLAIN in app internal storage (still hidden from gallery/Files,
// still deleted from the gallery on import, still protected by the
// app lock). Opening is now INSTANT — the blackhole file itself is
// handed to the player, no decrypt, no preview copy, no loading.
//
// WHY OPENING WAS SLOW BEFORE (the user asked): yes, it was the
// encryption. Every open decrypted the WHOLE file to a preview copy
// before the player could start — that is why photos, videos, audio
// and documents were ALL slow, and why big videos were slowest.
// On top of that, the modal pre-warm, the Open tap and the video
// thumbnail maker were all decrypting the SAME file at the same time
// into the same .tmp file, fighting each other. Both are gone now.
//
// WHAT THIS FILE DOES NOW:
// - openExternal: plain file -> chooser at once. Old encrypted file
//   -> decrypt-preview path once (until the one-time migration in
//   BlackHoleManager converts it to plain).
// - looksPlain: 12-byte magic check that tells plain from encrypted.
// - videoThumbnail / decodeSampledBitmap: plain files read straight
//   from the blackhole file; old encrypted files use the preview path.
// - MIME table is wider than the old mimeFor (heic/avif/3gp/m4v/
//   flac/opus/epub...), fallback is "*/*" — NEVER
//   "application/octet-stream" as the intent type, players reject it.
// - Works Android 9 (API 28) to 18: only APIs available since API 28
//   are used, with a version guard for queryIntentActivities flags.
// - No new library. Same package as BlackHoleManager/BlackHoleCrypto, so no
//   import is needed for them.
//
// HONEST NOTE: files are hidden, not encrypted, now. Anyone with
// the unlocked phone + the blackhole secret (or the app's internal files
// via USB/adb) can read them. The user accepted this: the app has no
// internet (except updates), so there is no leak path, and the app
// lock is the protection. Do NOT re-add encryption without asking.
package com.calnyx.app.blackhole

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URLConnection

sealed interface BlackHoleOpenResult {
    object Opened : BlackHoleOpenResult
    object NoViewer : BlackHoleOpenResult
    data class Failed(val msg: String) : BlackHoleOpenResult
}

object BlackHoleOpener {

    // OPENFIX 3: 64KB streaming buffer (was 8KB) — fewer cipher
    // calls, big videos decrypt noticeably faster. Never whole
    // file in RAM.
    private const val BUFFER_SIZE = 65536

    // OPENFIX 4: one decrypt at a time per file. Before this, the
    // modal pre-warm, the Open tap and the thumbnail maker all
    // decrypted the same file at once into the same .tmp file —
    // three decrypts fighting each other, which is a big part of
    // why opening was so slow (and could corrupt the preview).
    // Now a second caller waits, then reuses the finished preview.
    private val previewLocks = mutableMapOf<String, Mutex>()
    private fun lockFor(file: File): Mutex = synchronized(previewLocks) {
        previewLocks.getOrPut(file.absolutePath) { Mutex() }
    }

    // ── EXTERNAL OPEN (video / photo / audio / document) ──────────
    // Suspend: call from scope.launch in the Screen. The decrypt/copy
    // runs on Dispatchers.IO; only startActivity runs on Main.
    // onProgress reports 0f..1f while the preview is being prepared,
    // so the Screen can show a real "Opening... 47%" bar.
    // ── PLAIN OPEN (OPENFIX 4, 2026-11-06) ────────────────────────
    // The user removed encryption: blackhole files are now stored PLAIN
    // (hidden in app internal storage, invisible to gallery/Files,
    // deleted from the gallery on import, protected by the app lock).
    // So opening is INSTANT — the blackhole file itself is handed to the
    // player, no decrypt, no preview copy, no loading. Old files
    // that are still encrypted (from before) go through the decrypt
    // preview path once, until the one-time migration converts them.
    suspend fun openExternal(
        context: Context,
        file: File,
        onProgress: (Float) -> Unit = {}
    ): BlackHoleOpenResult {
        // Plain file: open it directly, right now.
        if (looksPlain(file)) {
            return withContext(Dispatchers.Main) {
                if (showChooser(context, file, file)) BlackHoleOpenResult.Opened
                else BlackHoleOpenResult.Failed("Could not open file")
            }
        }
        // Old encrypted file: decrypt to a preview first (once).
        val preview = try {
            preparePreview(context, file, onProgress)
        } catch (e: Exception) {
            return BlackHoleOpenResult.Failed(e.message ?: "Could not prepare file")
        }
        if (preview == null) return BlackHoleOpenResult.Failed("Could not prepare file")
        return withContext(Dispatchers.Main) {
            if (showChooser(context, preview, file)) BlackHoleOpenResult.Opened
            else BlackHoleOpenResult.Failed("Could not open file")
        }
    }

    // Show the "Open with" chooser for a file that is ready to play.
    // MUST be called on Main (startActivity). Returns true if the
    // chooser was shown. `toOpen` is the file the player reads;
    // `original` is the blackhole file (its extension picks the MIME).
    fun showChooser(context: Context, toOpen: File, original: File): Boolean {
        val mime = mimeForOpen(original)
        val uri: Uri = try {
            FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", toOpen
            )
        } catch (e: Exception) {
            return false
        }
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        // If no app handles this MIME, retry with */* before giving up.
        val finalIntent: Intent
        if (resolveActivities(context, viewIntent).isNotEmpty()) {
            finalIntent = viewIntent
        } else {
            val anyIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (resolveActivities(context, anyIntent).isEmpty()) return false
            finalIntent = anyIntent
        }
        return try {
            // Some OEMs (Android 9-12) ignore the FLAG on the chooser,
            // so grant the URI explicitly to every app that can open it.
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
            true
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    // Is this blackhole file stored PLAIN (not encrypted)? Reads only the
    // first 12 bytes and checks known file magics. An encrypted file
    // starts with a random 12-byte IV, so it never matches a magic.
    // Unknown types return false (safe): the caller then tries the
    // decrypt path, which falls back to a plain copy anyway.
    fun looksPlain(file: File): Boolean {
        return try {
            val head = ByteArray(12)
            var read = 0
            FileInputStream(file).use { src ->
                while (read < 12) {
                    val r = src.read(head, read, 12 - read)
                    if (r == -1) break
                    read += r
                }
            }
            if (read < 4) return false
            val b = head
            fun ascii(at: Int, len: Int): String =
                String(b, at, len, Charsets.US_ASCII)
            // JPEG FF D8 FF | PNG | GIF | WebP/AVI/WAV "RIFF"
            if (b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte()) return true
            if (b[0] == 0x89.toByte() && ascii(1, 3) == "PNG") return true
            if (ascii(0, 4) == "GIF8") return true
            if (ascii(0, 4) == "RIFF") return true
            // MP4 / MOV / M4V / 3GP: "ftyp" at offset 4
            if (read >= 8 && ascii(4, 4) == "ftyp") return true
            // MKV / WebM: 1A 45 DF A3
            if (b[0] == 0x1A.toByte() && b[1] == 0x45.toByte() &&
                b[2] == 0xDF.toByte() && b[3] == 0xA3.toByte()) return true
            // MP3 "ID3" or frame sync | OGG "OggS" | FLAC "fLaC"
            if (ascii(0, 3) == "ID3") return true
            if (b[0] == 0xFF.toByte() && (b[1].toInt() and 0xE0) == 0xE0) return true
            if (ascii(0, 4) == "OggS") return true
            if (ascii(0, 4) == "fLaC") return true
            // PDF "%PDF" | ZIP-based docs (docx/xlsx/pptx/epub) "PK"
            if (ascii(0, 4) == "%PDF") return true
            if (b[0] == 0x50.toByte() && b[1] == 0x4B.toByte()) return true
            false
        } catch (_: Exception) {
            false
        }
    }

    // OPENFIX 4: prewarm is gone. It existed only to hide the
    // decrypt time; with encryption removed there is nothing to
    // pre-warm — a plain file opens instantly on tap.

    // ── VIDEO THUMBNAIL (OPENFIX 3) ───────────────────────────────
    // The grid used to show a black box + play icon for every video
    // because no frame was ever extracted. This pulls one frame
    // (at 1 second) out of the decrypted preview, downscales it to
    // ~400px wide and caches it as a JPEG in cache/blackhole_thumbs.
    // SUSPEND + blocking IO: call only from Dispatchers.IO (the
    // Screen does that in BlackHoleVideoThumbnail). First call for a
    // big video still needs the decrypt (preparePreview, reused if
    // the video was already opened); after that the cached JPEG
    // loads instantly. Returns null if no frame can be read.
    suspend fun videoThumbnail(context: Context, file: File): Bitmap? {
        val thumbDir = File(context.cacheDir, "blackhole_thumbs").also { it.mkdirs() }
        val thumb = File(thumbDir, file.name + ".jpg")
        if (thumb.exists() && thumb.length() > 0) {
            try {
                return BitmapFactory.decodeFile(thumb.absolutePath)
            } catch (_: Exception) {
            }
        }
        // OPENFIX 4: plain files go straight to the retriever — no
        // decrypt, no preview copy, so the frame is fast. Old
        // encrypted files still go through the preview (once, until
        // the one-time migration converts them to plain).
        val source = if (looksPlain(file)) file else try {
            preparePreview(context, file)
        } catch (_: Exception) {
            null
        } ?: return null
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(source.absolutePath)
            val frame = retriever.getFrameAtTime(
                1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            ) ?: return null
            val w = frame.width
            val h = frame.height
            if (w <= 0 || h <= 0) return null
            val scale = 400f / w.toFloat()
            val small = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    frame, 400, (h * scale).toInt().coerceAtLeast(1), true
                )
            } else {
                frame
            }
            try {
                FileOutputStream(thumb).use { out ->
                    small.compress(Bitmap.CompressFormat.JPEG, 85, out)
                }
            } catch (_: Exception) {
            }
            if (small !== frame) frame.recycle()
            small
        } catch (_: Exception) {
            null
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    // Drop the cached preview + thumbnail for one blackhole file.
    // Call when the blackhole file is deleted, so no stale frame or
    // decrypted copy lingers in cache.
    fun dropCaches(context: Context, file: File) {
        try { File(File(context.cacheDir, "blackhole_preview"), file.name).delete() } catch (_: Exception) {}
        try { File(File(context.cacheDir, "blackhole_thumbs"), file.name + ".jpg").delete() } catch (_: Exception) {}
    }

    // Decrypt (or plain-copy) the blackhole file to cache/blackhole_preview
    // ONCE, then reuse it. Runs on Dispatchers.IO. Returns null only
    // if neither decrypt nor plain copy produced a usable file.
    // OPENFIX 3: onProgress reports 0f..1f of the decrypt, so the
    // Screen shows a real percentage instead of an endless spinner.
    private suspend fun preparePreview(
        context: Context,
        file: File,
        onProgress: (Float) -> Unit = {}
    ): File? = lockFor(file).withLock {
        withContext(Dispatchers.IO) {
            val previewDir = File(context.cacheDir, "blackhole_preview").also { it.mkdirs() }
            val preview = File(previewDir, file.name)
            // Reuse check: a finished preview has exactly the expected
            // length — encrypted: file minus 12-byte IV minus 16-byte
            // GCM tag (28); plain: same length as the blackhole file.
            // A half-written preview from a killed earlier open fails
            // this check and is re-decrypted instead of played broken.
            if (preview.exists() && preview.length() > 0) {
                val expectedEncrypted = file.length() - 28
                if (preview.length() == expectedEncrypted || preview.length() == file.length()) {
                    onProgress(1f)
                    return@withContext preview
                } else {
                    try { preview.delete() } catch (_: Exception) {}
                }
            }
            val expected = (file.length() - 28).coerceAtLeast(1L)
            val tmp = File(previewDir, "${file.name}.tmp")
            try {
                try {
                    FileInputStream(file).use { src ->
                        FileOutputStream(tmp).use { dst ->
                            BlackHoleCrypto.decryptStream(src, dst) { written ->
                                onProgress((written.toFloat() / expected).coerceIn(0f, 1f))
                            }
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

    // Wider MIME table than the old BlackHoleManager.mimeFor. Fallback is
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
    // already does that in BlackHolePhotoThumbnail / BlackHolePhotoImage).
    // Decrypts streaming into a temp FILE, decodes bounds + sampled
    // bitmap FROM THAT FILE, deletes the temp file. RAM use is only
    // the final sampled bitmap. No decryptToBytes, no readBytes.
    fun decodeSampledBitmap(file: File, reqSize: Int): Bitmap? {
        // OPENFIX 4: plain files decode straight from the blackhole file
        // — no decrypt, no temp copy. Photos open instantly now.
        if (looksPlain(file)) {
            return try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
                var sample = 1
                while (bounds.outWidth / sample > reqSize ||
                    bounds.outHeight / sample > reqSize
                ) sample *= 2
                BitmapFactory.decodeFile(
                    file.absolutePath,
                    BitmapFactory.Options().apply { inSampleSize = sample }
                )
            } catch (_: Exception) {
                null
            }
        }
        // Old encrypted file: streaming decrypt to a temp FILE, then
        // decode sampled FROM that file (same as before).
        var tmp: File? = null
        return try {
            tmp = File.createTempFile("blackhole_thumb", ".tmp")
            try {
                FileInputStream(file).use { src ->
                    FileOutputStream(tmp).use { dst ->
                        BlackHoleCrypto.decryptStream(src, dst)
                    }
                }
            } catch (_: Exception) {
                // Old plain file: stream-copy it instead.
                try { tmp.delete() } catch (_: Exception) {}
                tmp = File.createTempFile("blackhole_thumb", ".tmp")
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

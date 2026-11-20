// FILE: app/src/main/java/com/calnyx/app/clipboard/ClipboardRepository.kt
// REPLACE FILE - Clipboard fix (2026-11-20).
// Target: app/src/main/java/com/calnyx/app/clipboard/ClipboardRepository.kt
// First package line: package com.calnyx.app.clipboard
// First class line: class ClipboardRepository(private val context: Context)
//
// WHAT CHANGED vs the 2026-11-06 version (3 fixes):
// 1. DEDUP IS PERSISTENT NOW. The old 800 ms guard was a @Volatile
//    field on a Repository instance - every new Repository (service,
//    checkNow, screen, capture activity) reset it, so one copy could
//    save twice and a deliberate re-copy was a coin toss. Dedup now
//    lives in ClipboardCapture (SharedPreferences, same file as the
//    retention setting) and is shared by every caller: same text
//    within 2 seconds is skipped, a deliberate re-copy later than
//    that still inserts a NEW row, as the user asked.
// 2. IMAGE DECODE READS THE BYTES ONCE. The old code opened the
//    content Uri twice (bounds, then bitmap). Some providers hand
//    out one-shot streams, so the second open failed and the image
//    copy silently vanished. Now readBytes() once, decode bounds +
//    bitmap from the same bytes. OutOfMemory is also caught, so a
//    huge image can never crash the service.
// 3. SEARCH ESCAPES LIKE WILDCARDS. A query containing % or _ used
//    to match almost everything (DAO: LIKE '%' || :query || '%').
//    search() now escapes \, % and _ first; ClipboardDao adds
//    ESCAPE '\' to both LIKEs. Everything else is unchanged:
//    insert rules, copyAgain (re-copy inserts a new row on top),
//    pruneExpired, deleteItem (image file deleted only when no other
//    row points at it).
package com.calnyx.app.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class ClipboardRepository(private val context: Context) {

    private val db = ClipboardDatabase.get(context)
    private val dao = db.clipboardDao()

    fun observeAll(): Flow<List<ClipboardItem>> = dao.observeAll()

    fun search(query: String): Flow<List<ClipboardItem>> {
        // Escape LIKE wildcards so "100%" searches for "100%",
        // not for everything starting with "100".
        val escaped = query
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
        return dao.search(escaped)
    }

    /**
     * Saves copied text. Returns the new row id, or -1L when the
     * text was blank or skipped by the persistent 2-second dedup.
     */
    suspend fun insertText(text: String): Long {
        val clean = text.trim()
        if (clean.isEmpty()) return -1L
        if (ClipboardCapture.isDuplicateText(context, clean)) return -1L
        val now = System.currentTimeMillis()
        val id = dao.insert(
            ClipboardItem(type = "TEXT", text = clean, createdAt = now, updatedAt = now)
        )
        if (id > 0L) ClipboardCapture.markSavedText(context, clean)
        return id
    }

    /**
     * Saves a copied image: downscales so the longest side is at
     * most 1280 px, compresses to JPEG quality 85, and stores the
     * file under filesDir/clipboard_images/. Returns the new row id,
     * or null when the Uri could not be decoded as an image.
     * Bytes are read ONCE (one-shot content Uris failed on the
     * second open in the old version).
     */
    suspend fun insertImage(uri: Uri): Long? = withContext(Dispatchers.IO) {
        try {
            if (ClipboardCapture.isDuplicateImage(context, uri.toString())) {
                return@withContext null
            }
            val bytes = try {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            } catch (_: Exception) {
                null
            } ?: return@withContext null
            if (bytes.isEmpty()) return@withContext null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
            var sample = 1
            val longest = maxOf(bounds.outWidth, bounds.outHeight)
            while (longest / sample > 1280) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                ?: return@withContext null
            val dir = File(context.filesDir, "clipboard_images").apply { if (!exists()) mkdirs() }
            val file = File(dir, "clip_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
            }
            if (!bitmap.isRecycled) bitmap.recycle()
            val now = System.currentTimeMillis()
            val id = dao.insert(
                ClipboardItem(
                    type = "IMAGE",
                    text = "",
                    imagePath = file.absolutePath,
                    createdAt = now,
                    updatedAt = now
                )
            )
            if (id > 0L) ClipboardCapture.markSavedImage(context, uri.toString())
            id
        } catch (_: Throwable) {
            // Includes OutOfMemoryError on a huge image: never crash
            // the service for one copy.
            null
        }
    }

    /**
     * Re-copies an item to the system clipboard AND inserts a new
     * row, so it appears at the top of the list. Text rows copy
     * their text; image rows copy the image Uri (via FileProvider
     * when it is configured, plain file Uri as fallback).
     * Returns the new row id, or -1L when copying failed.
     * The new row's fingerprint is marked saved, so the capture
     * activity triggered by our own setPrimaryClip() does NOT
     * insert the same text a second time.
     */
    suspend fun copyAgain(item: ClipboardItem): Long {
        val clipboard =
            context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                ?: return -1L
        try {
            if (item.type == "IMAGE" && item.imagePath != null) {
                val file = File(item.imagePath)
                if (!file.exists()) return -1L
                val uri = try {
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                } catch (_: Exception) {
                    Uri.fromFile(file)
                }
                clipboard.setPrimaryClip(
                    ClipData.newUri(context.contentResolver, "Image", uri)
                )
                val now = System.currentTimeMillis()
                val id = dao.insert(item.copy(id = 0L, createdAt = now, updatedAt = now))
                if (id > 0L) ClipboardCapture.markSavedImage(context, uri.toString())
                return id
            } else {
                if (item.text.isBlank()) return -1L
                clipboard.setPrimaryClip(
                    ClipData.newPlainText("CalNyx Clipboard", item.text)
                )
                // A deliberate Copy tap must always create a row,
                // so insert directly (not through the dedup guard).
                val now = System.currentTimeMillis()
                val id = dao.insert(
                    ClipboardItem(type = "TEXT", text = item.text, createdAt = now, updatedAt = now)
                )
                if (id > 0L) ClipboardCapture.markSavedText(context, item.text)
                return id
            }
        } catch (_: Exception) {
            return -1L
        }
    }

    suspend fun updateItem(item: ClipboardItem) {
        dao.update(item.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun delete(id: Long) {
        dao.deleteById(id)
    }

    suspend fun deleteItem(item: ClipboardItem) {
        dao.deleteById(item.id)
        val path = item.imagePath ?: return
        // Another row (e.g. a re-copy) may share this file.
        if (dao.countByImagePath(path) == 0) {
            withContext(Dispatchers.IO) {
                try {
                    File(path).delete()
                } catch (_: Exception) {
                }
            }
        }
    }

    suspend fun deleteAll() {
        withContext(Dispatchers.IO) {
            try {
                val dir = File(context.filesDir, "clipboard_images")
                dir.listFiles()?.forEach { it.delete() }
            } catch (_: Exception) {
            }
        }
        dao.deleteAll()
    }

    /**
     * Deletes every row older than the current retention setting
     * (default 30 days) and removes their image files from disk.
     * Called on insert, on service start, on screen open, and
     * immediately after the retention setting changes.
     */
    suspend fun pruneExpired() {
        val days = ClipboardPrefs.getRetentionDays(context)
        val cutoff = System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L
        val stalePaths = try {
            dao.imagePathsOlderThan(cutoff)
        } catch (_: Exception) {
            emptyList()
        }
        dao.deleteOlderThan(cutoff)
        withContext(Dispatchers.IO) {
            stalePaths.forEach { path ->
                try {
                    File(path).delete()
                } catch (_: Exception) {
                }
            }
        }
    }
}

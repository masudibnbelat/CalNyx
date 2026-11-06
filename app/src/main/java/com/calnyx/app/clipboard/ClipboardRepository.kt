// FILE: app/src/main/java/com/calnyx/app/clipboard/ClipboardRepository.kt
// NEW FILE - Clipboard feature (2026-11-06).
// Target: app/src/main/java/com/calnyx/app/clipboard/ClipboardRepository.kt
// First package line: package com.calnyx.app.clipboard
// First class line: class ClipboardRepository(private val context: Context)
// Single place for every clipboard write. Rules:
// - insertText ignores blank text.
// - DOUBLE-FIRE GUARD (800 ms): the clipboard listener can fire
//   twice for one copy. The same text within 800 ms of the last
//   insert is skipped. A DELIBERATE re-copy later than that
//   inserts a NEW row, as the user asked (same text twice = two rows).
// - copyAgain re-copies the item to the system clipboard AND
//   inserts a new row, so the re-copied item jumps to the top.
// - pruneExpired deletes rows older than the retention setting
//   and removes their image files from disk too.
// - delete(id) removes the image file ONLY when no other row
//   still points at it (a re-copied image row shares the file).
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

    @Volatile
    private var lastInsertedText: String? = null

    @Volatile
    private var lastInsertedAt: Long = 0L

    fun observeAll(): Flow<List<ClipboardItem>> = dao.observeAll()

    fun search(query: String): Flow<List<ClipboardItem>> = dao.search(query)

    /**
     * Saves copied text. Returns the new row id, or -1L when the
     * text was blank or skipped by the 800 ms double-fire guard.
     */
    suspend fun insertText(text: String): Long {
        val clean = text.trim()
        if (clean.isEmpty()) return -1L
        val now = System.currentTimeMillis()
        if (clean == lastInsertedText && now - lastInsertedAt < 800L) return -1L
        val id = dao.insert(
            ClipboardItem(type = "TEXT", text = clean, createdAt = now, updatedAt = now)
        )
        lastInsertedText = clean
        lastInsertedAt = now
        return id
    }

    /**
     * Saves a copied image: downscales so the longest side is at
     * most 1280 px, compresses to JPEG quality 85, and stores the
     * file under filesDir/clipboard_images/. Returns the new row id,
     * or null when the Uri could not be decoded as an image.
     */
    suspend fun insertImage(uri: Uri): Long? = withContext(Dispatchers.IO) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
            var sample = 1
            val longest = maxOf(bounds.outWidth, bounds.outHeight)
            while (longest / sample > 1280) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, opts)
            } ?: return@withContext null
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
            id
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Re-copies an item to the system clipboard AND inserts a new
     * row, so it appears at the top of the list. Text rows copy
     * their text; image rows copy the image Uri (via FileProvider
     * when it is configured, plain file Uri as fallback).
     * Returns the new row id, or -1L when copying failed.
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
                return dao.insert(item.copy(id = 0L, createdAt = now, updatedAt = now))
            } else {
                if (item.text.isBlank()) return -1L
                clipboard.setPrimaryClip(
                    ClipData.newPlainText("CalNyx Clipboard", item.text)
                )
                // insertText would apply the 800 ms guard; a deliberate
                // Copy tap must always create a row, so insert directly.
                val now = System.currentTimeMillis()
                val id = dao.insert(
                    ClipboardItem(type = "TEXT", text = item.text, createdAt = now, updatedAt = now)
                )
                lastInsertedText = item.text
                lastInsertedAt = now
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

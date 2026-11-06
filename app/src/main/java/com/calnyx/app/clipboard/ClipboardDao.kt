// FILE: app/src/main/java/com/calnyx/app/clipboard/ClipboardDao.kt
// NEW FILE - Clipboard feature (2026-11-06).
// Target: app/src/main/java/com/calnyx/app/clipboard/ClipboardDao.kt
// First package line: package com.calnyx.app.clipboard
// First interface line: @Dao interface ClipboardDao
// All reads are Flows so the screen updates the moment a new
// copy lands, with no manual refresh. search() matches the
// copied text AND the image note, so images are findable too.
package com.calnyx.app.clipboard

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ClipboardDao {

    @Query("SELECT * FROM clipboard_items ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ClipboardItem>>

    @Query(
        "SELECT * FROM clipboard_items " +
            "WHERE text LIKE '%' || :query || '%' " +
            "OR note LIKE '%' || :query || '%' " +
            "ORDER BY createdAt DESC"
    )
    fun search(query: String): Flow<List<ClipboardItem>>

    @Insert
    suspend fun insert(item: ClipboardItem): Long

    @Update
    suspend fun update(item: ClipboardItem)

    @Query("DELETE FROM clipboard_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM clipboard_items WHERE createdAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int

    @Query("DELETE FROM clipboard_items")
    suspend fun deleteAll()

    @Query("SELECT imagePath FROM clipboard_items WHERE createdAt < :cutoff AND imagePath IS NOT NULL")
    suspend fun imagePathsOlderThan(cutoff: Long): List<String>

    @Query("SELECT COUNT(*) FROM clipboard_items WHERE imagePath = :path")
    suspend fun countByImagePath(path: String): Int
}

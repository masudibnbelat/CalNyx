// FILE: app/src/main/java/com/calnyx/app/clipboard/ClipboardItem.kt
// NEW FILE - Clipboard feature (2026-11-06).
// Target: app/src/main/java/com/calnyx/app/clipboard/ClipboardItem.kt
// First package line: package com.calnyx.app.clipboard
// First class line: @Entity(tableName = "clipboard_items") data class ClipboardItem
// One row per copy event. The SAME text copied twice = TWO rows
// (no UNIQUE constraint on text, by design). Newest first is
// done by the DAO (ORDER BY createdAt DESC), not here.
// type is "TEXT" or "IMAGE". For a pure image row, text is ""
// and imagePath points at a JPEG inside filesDir/clipboard_images/.
// note is an optional caption, editable from the card (images only
// use it as their caption; text rows keep it null).
package com.calnyx.app.clipboard

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "clipboard_items")
data class ClipboardItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val type: String = "TEXT", // "TEXT" or "IMAGE"
    val text: String = "",
    val imagePath: String? = null,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

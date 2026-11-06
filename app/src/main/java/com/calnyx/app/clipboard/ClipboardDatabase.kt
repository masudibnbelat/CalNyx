// FILE: app/src/main/java/com/calnyx/app/clipboard/ClipboardDatabase.kt
// NEW FILE - Clipboard feature (2026-11-06).
// Target: app/src/main/java/com/calnyx/app/clipboard/ClipboardDatabase.kt
// First package line: package com.calnyx.app.clipboard
// First class line: @Database(entities = [ClipboardItem::class], version = 1, exportSchema = false)
// Fully offline: a plain Room/SQLite file ("clipboard.db") in the
// app's private storage. No server, no account, no extra database.
// Room is ALREADY a dependency (NavStateDatabase), so nothing new
// is added to build.gradle.kts for this file.
package com.calnyx.app.clipboard

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ClipboardItem::class], version = 1, exportSchema = false)
abstract class ClipboardDatabase : RoomDatabase() {

    abstract fun clipboardDao(): ClipboardDao

    companion object {
        @Volatile
        private var INSTANCE: ClipboardDatabase? = null

        fun get(context: Context): ClipboardDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ClipboardDatabase::class.java,
                    "clipboard.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
        }
    }
}

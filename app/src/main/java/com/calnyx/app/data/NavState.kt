package com.calnyx.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "nav_state")
data class NavStateEntity(
    @PrimaryKey val id: Int = 1,
    val route: String
)

@Dao
interface NavStateDao {

    @Query("SELECT * FROM nav_state WHERE id = 1")
    fun observe(): Flow<NavStateEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(state: NavStateEntity)
}

// One CalTape sheet. The tape lines themselves live in the main history
// table and point here through their worksheetId.
@Entity(tableName = "sheets")
data class SheetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface SheetDao {

    @Query("SELECT * FROM sheets ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<SheetEntity>>

    @Query("SELECT * FROM sheets ORDER BY createdAt ASC")
    suspend fun getAll(): List<SheetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sheet: SheetEntity)

    @Query("DELETE FROM sheets WHERE id = :id")
    suspend fun deleteById(id: String)
}

// A tiny separate Room database for app state: the last open page and
// the CalTape sheets. The main history database is not touched, so no
// history is ever wiped by changes here.
@Database(
    entities = [NavStateEntity::class, SheetEntity::class],
    version = 2,
    exportSchema = false
)
abstract class NavStateDatabase : RoomDatabase() {

    abstract fun navStateDao(): NavStateDao
    abstract fun sheetDao(): SheetDao

    companion object {
        @Volatile
        private var instance: NavStateDatabase? = null

        fun get(context: Context): NavStateDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    NavStateDatabase::class.java,
                    "nav_state_database"
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
        }
    }
}

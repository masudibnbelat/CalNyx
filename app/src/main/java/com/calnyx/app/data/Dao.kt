package com.calnyx.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WorksheetDao {
    @Query("SELECT * FROM worksheets ORDER BY updatedAt DESC")
    fun getAllWorksheets(): Flow<List<WorksheetEntity>>

    @Query("SELECT * FROM worksheets WHERE id = :id")
    fun getWorksheetById(id: String): Flow<WorksheetEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(worksheet: WorksheetEntity)

    @Update
    suspend fun update(worksheet: WorksheetEntity)

    @Delete
    suspend fun delete(worksheet: WorksheetEntity)

    @Query("DELETE FROM worksheets WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY createdAt DESC LIMIT 200")
    fun getAllHistory(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE calculatorType = :type ORDER BY createdAt DESC LIMIT 100")
    fun getHistoryByType(type: String): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: HistoryEntity)

    @Delete
    suspend fun delete(history: HistoryEntity)

    @Query("DELETE FROM history")
    suspend fun clearAll()

    @Query("DELETE FROM history WHERE calculatorType = :type")
    suspend fun clearByType(type: String)

    // --- CalTape sheets: tape lines belong to a sheet via worksheetId ---
    @Query("SELECT * FROM history WHERE calculatorType = :type AND worksheetId = :worksheetId ORDER BY createdAt DESC LIMIT 200")
    fun getTapeLines(type: String, worksheetId: String): Flow<List<HistoryEntity>>

    @Query("DELETE FROM history WHERE calculatorType = :type AND worksheetId = :worksheetId")
    suspend fun clearTapeSheet(type: String, worksheetId: String)

    // One-time: lines saved before sheets existed have no worksheetId.
    @Query("UPDATE history SET worksheetId = :worksheetId WHERE calculatorType = :type AND worksheetId IS NULL")
    suspend fun assignLegacyTapeLines(type: String, worksheetId: String)
}

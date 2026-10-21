package com.calnyx.app.data

import kotlinx.coroutines.flow.Flow

class CalNyxRepository(
    private val worksheetDao: WorksheetDao,
    private val historyDao: HistoryDao
) {
    // Worksheets
    fun getAllWorksheets(): Flow<List<WorksheetEntity>> = worksheetDao.getAllWorksheets()
    fun getWorksheetById(id: String): Flow<WorksheetEntity?> = worksheetDao.getWorksheetById(id)
    suspend fun insertWorksheet(worksheet: WorksheetEntity) = worksheetDao.insert(worksheet)
    suspend fun updateWorksheet(worksheet: WorksheetEntity) = worksheetDao.update(worksheet)
    suspend fun deleteWorksheet(worksheet: WorksheetEntity) = worksheetDao.delete(worksheet)

    // History
    fun getAllHistory(): Flow<List<HistoryEntity>> = historyDao.getAllHistory()
    fun getHistoryByType(type: String): Flow<List<HistoryEntity>> = historyDao.getHistoryByType(type)
    suspend fun insertHistory(history: HistoryEntity) = historyDao.insert(history)
    suspend fun deleteHistory(history: HistoryEntity) = historyDao.delete(history)
    suspend fun clearHistory() = historyDao.clearAll()
}

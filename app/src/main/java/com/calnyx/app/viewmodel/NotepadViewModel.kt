package com.calnyx.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.calnyx.app.CalNyxApp
import com.calnyx.app.data.CalNyxRepository
import com.calnyx.app.data.WorksheetEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NotepadViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: CalNyxRepository

    init {
        val db = (application as CalNyxApp).database
        repository = CalNyxRepository(db.worksheetDao(), db.historyDao())
    }

    val worksheets: StateFlow<List<WorksheetEntity>> = repository.getAllWorksheets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentWorksheet = MutableStateFlow<WorksheetEntity?>(null)
    val currentWorksheet: StateFlow<WorksheetEntity?> = _currentWorksheet.asStateFlow()

    fun selectWorksheet(worksheet: WorksheetEntity) {
        _currentWorksheet.value = worksheet
    }

    fun createWorksheet(title: String) {
        viewModelScope.launch {
            val newSheet = WorksheetEntity(title = title)
            repository.insertWorksheet(newSheet)
            _currentWorksheet.value = newSheet
        }
    }

    fun updateTitle(title: String) {
        val current = _currentWorksheet.value ?: return
        val updated = current.copy(title = title, updatedAt = System.currentTimeMillis())
        _currentWorksheet.value = updated
        viewModelScope.launch {
            repository.updateWorksheet(updated)
        }
    }

    fun updateContent(content: String) {
        val current = _currentWorksheet.value ?: return
        val updated = current.copy(content = content, updatedAt = System.currentTimeMillis())
        _currentWorksheet.value = updated
        viewModelScope.launch {
            repository.updateWorksheet(updated)
        }
    }

    fun deleteWorksheet(worksheet: WorksheetEntity) {
        viewModelScope.launch {
            repository.deleteWorksheet(worksheet)
            if (_currentWorksheet.value?.id == worksheet.id) {
                _currentWorksheet.value = null
            }
        }
    }
}

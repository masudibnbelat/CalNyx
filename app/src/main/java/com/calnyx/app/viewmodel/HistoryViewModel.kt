package com.calnyx.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.calnyx.app.CalNyxApp
import com.calnyx.app.data.CalNyxRepository
import com.calnyx.app.data.HistoryEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: CalNyxRepository

    init {
        val db = (application as CalNyxApp).database
        repository = CalNyxRepository(db.worksheetDao(), db.historyDao())
    }

    val history: StateFlow<List<HistoryEntity>> = repository.getAllHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun clearAll() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }
}

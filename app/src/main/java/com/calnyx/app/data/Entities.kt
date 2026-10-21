package com.calnyx.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "worksheets")
data class WorksheetEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val content: String = "",          // full notepad text with calculations
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val expression: String,
    val result: String,
    val calculatorType: String,        // normal, scientific, financial, matrix, business
    val worksheetId: String? = null,   // optional link to worksheet
    val createdAt: Long = System.currentTimeMillis()
)

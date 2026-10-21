package com.calnyx.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calnyx.app.data.WorksheetEntity
import com.calnyx.app.viewmodel.NotepadViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotepadCalculatorScreen(
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
    viewModel: NotepadViewModel = viewModel()
) {
    val worksheets by viewModel.worksheets.collectAsState()
    val currentWorksheet by viewModel.currentWorksheet.collectAsState()
    var showNewDialog by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notepad Calculator") },
                actions = {
                    IconButton(onClick = onToggleTheme) {
                        Icon(
                            if (darkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Toggle Theme"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showNewDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "New Worksheet")
            }
        }
    ) { padding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Sidebar - Worksheets list
            Column(
                modifier = Modifier
                    .width(140.dp)
                    .fillMaxHeight()
                    .padding(8.dp)
            ) {
                Text(
                    "Worksheets",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(8.dp)
                )
                LazyColumn {
                    items(worksheets) { sheet ->
                        WorksheetItem(
                            worksheet = sheet,
                            isSelected = currentWorksheet?.id == sheet.id,
                            onClick = { viewModel.selectWorksheet(sheet) },
                            onDelete = { viewModel.deleteWorksheet(sheet) }
                        )
                    }
                }
            }

            VerticalDivider()

            // Main notepad area
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(12.dp)
            ) {
                if (currentWorksheet != null) {
                    OutlinedTextField(
                        value = currentWorksheet!!.title,
                        onValueChange = { viewModel.updateTitle(it) },
                        label = { Text("Title") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = currentWorksheet!!.content,
                        onValueChange = { viewModel.updateContent(it) },
                        label = { Text("Write calculations & notes like a notebook...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        placeholder = {
                            Text(
                                "Example:\n" +
                                "Rice  = 120 × 3 = 360\n" +
                                "Oil   = 250\n" +
                                "Total = 360 + 250 = 610\n\n" +
                                "Notes: Market list for this week..."
                            )
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tip: Write expressions and results. Everything is saved automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.NoteAlt,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("No worksheet selected")
                            Text(
                                "Create a new worksheet to start writing calculations",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }

    if (showNewDialog) {
        AlertDialog(
            onDismissRequest = { showNewDialog = false },
            title = { Text("New Worksheet") },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    label = { Text("Worksheet name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newTitle.isNotBlank()) {
                        viewModel.createWorksheet(newTitle.trim())
                        newTitle = ""
                        showNewDialog = false
                    }
                }) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun WorksheetItem(
    worksheet: WorksheetEntity,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd MMM", Locale.getDefault())
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = worksheet.title,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                Text(
                    text = dateFormat.format(Date(worksheet.updatedAt)),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

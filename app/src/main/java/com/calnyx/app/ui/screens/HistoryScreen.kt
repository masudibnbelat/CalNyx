// FILE: app/src/main/java/com/calnyx/app/ui/screens/HistoryScreen.kt
package com.calnyx.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calnyx.app.viewmodel.HistoryViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    viewModel: HistoryViewModel = viewModel()
) {
    // onBack is kept for the existing NavGraph call — the back button
    // now lives in the floating top bar drawn by NavGraph.
    val history by viewModel.history.collectAsState()
    val groupedHistory = remember(history) {
        history.groupBy { dayLabel(it.createdAt) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        // Clearance for the floating top bar drawn by NavGraph
        Spacer(modifier = Modifier.height(56.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "History",
                fontSize = 28.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.weight(1f))
            if (history.isNotEmpty()) {
                TextButton(onClick = { viewModel.clearAll() }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Clear all",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Clear all")
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (history.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No history yet",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                groupedHistory.forEach { (day, dayItems) ->
                    item(key = "header-$day") {
                        Text(
                            text = day,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                    items(dayItems) { item ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = formatForDisplay(item.expression),
                                fontSize = 20.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.End
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = formatForDisplay(item.result),
                                fontSize = 34.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.End
                            )
                        }
                    }
                    item(key = "divider-$day") {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun dayLabel(timestamp: Long): String {
    val itemCalendar = Calendar.getInstance().apply { timeInMillis = timestamp }
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    return when {
        isSameDay(itemCalendar, today) -> "Today"
        isSameDay(itemCalendar, yesterday) -> "Yesterday"
        else -> SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(timestamp))
    }
}

private fun isSameDay(a: Calendar, b: Calendar): Boolean {
    return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
}

private fun formatForDisplay(raw: String): String {
    var display = raw
        .replace("sqrt(", "√(")
        .replace("log10(", "log(")
        .replace("pi", "π")
        .replace("*", "×")
        .replace("/", "÷")
        .replace("-", "−")
    return display.replace(Regex("\\d+(\\.\\d+)?")) { match ->
        val parts = match.value.split(".")
        val grouped = parts[0].reversed().chunked(3).joinToString(",").reversed()
        if (parts.size > 1) grouped + "." + parts[1] else grouped
    }
}

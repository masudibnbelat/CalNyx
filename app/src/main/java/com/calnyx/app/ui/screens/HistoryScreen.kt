// THEME + % VERSION (2026-11-20) - REPLACE HistoryScreen.kt.
// Colours were already theme variables (background, onSurface,
// onSurfaceVariant, outline) - kichu bodlayni. Sizes chilo fixed
// dp/sp (20.dp, 56.dp, 28.sp, 34.sp...) - ekhon sob % :
// BoxWithConstraints-er windowW/windowH/minDim theke. Logic,
// grouping, clear-all - sob ager motoi.
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

    // % SIZES: window-er % - kono fixed dp/sp nei (1.dp hairline
    // chara). 360x760 phone-e ager motoi dekhabe.
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val windowW = maxWidth
        val windowH = maxHeight
        val minDim = minOf(windowW, windowH)
        val sidePad = windowW * 0.055f // 20dp
        val topBarClearance = windowH * 0.074f // 56dp
        val titleFont = (minDim.value * 0.078f).sp // 28sp
        val clearIcon = minDim * 0.05f // 18dp
        val gapXs = windowW * 0.011f // 4dp
        val gapSm = windowH * 0.011f // 8dp
        val dayFont = (minDim.value * 0.042f).sp // 15sp
        val dayPadV = windowH * 0.016f // 12dp
        val itemPadV = windowH * 0.013f // 10dp
        val exprFont = (minDim.value * 0.056f).sp // 20sp
        val gapTiny = windowH * 0.003f // 2dp
        val resultFont = (minDim.value * 0.094f).sp // 34sp
        val dividerPadV = windowH * 0.011f // 8dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = sidePad)
        ) {
            // Clearance for the floating top bar drawn by NavGraph
            Spacer(modifier = Modifier.height(topBarClearance))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "History",
                    fontSize = titleFont,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.weight(1f))
                if (history.isNotEmpty()) {
                    TextButton(onClick = { viewModel.clearAll() }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Clear all",
                            modifier = Modifier.size(clearIcon)
                        )
                        Spacer(modifier = Modifier.width(gapXs))
                        Text("Clear all")
                    }
                }
            }

            Spacer(modifier = Modifier.height(gapSm))

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
                                fontSize = dayFont,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = dayPadV)
                            )
                        }
                        items(dayItems) { item ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = itemPadV),
                                horizontalAlignment = Alignment.End
                            ) {
                                Text(
                                    text = formatForDisplay(item.expression),
                                    fontSize = exprFont,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.End
                                )
                                Spacer(modifier = Modifier.height(gapTiny))
                                Text(
                                    text = formatForDisplay(item.result),
                                    fontSize = resultFont,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.End
                                )
                            }
                        }
                        item(key = "divider-$day") {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                modifier = Modifier.padding(vertical = dividerPadV)
                            )
                        }
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

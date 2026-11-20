// FILE: app/src/main/java/com/calnyx/app/BlueFilm/ScreenRecordToolbarActivity.kt
// NEW FILE - recording toolbar as a transparent Activity (2026-11-20).
//
// Opens when the floating ball (or the mini ball) is tapped while
// a screen recording runs. Same pattern as BallPanelActivity:
// translucent, its own task, never in recents, tap the dim
// background or Back to close - the recording keeps running.
//
// Tools (icon + label, % sizes, theme colours):
//   Pen (freehand), Circle, Rectangle, Triangle, Clear, Stop.
// Tapping a tool selects it AND closes the panel, so the user
// can draw at once; tapping the active tool again turns it off.
// Clear wipes all marks and KEEPS the panel open (clear, then
// pick the next tool). Stop ends the recording and closes.
// The header shows a red dot + the elapsed time (1 / sec); if
// the recording ends elsewhere, the panel closes itself.
package com.calnyx.app.BlueFilm

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChangeHistory
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calnyx.app.ui.theme.CalNyxRed
import com.calnyx.app.ui.theme.CalNyxTheme
import kotlinx.coroutines.delay

class ScreenRecordToolbarActivity : ComponentActivity() {

    private var elapsedSec by mutableStateOf(0L)
    private var activeTool by mutableStateOf(DrawTool.NONE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Show on the lock screen too, like the ball panel.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
        }
        // Nothing to do here when no recording runs.
        if (!ScreenRecordService.isRecording) {
            finish()
            return
        }
        // Belt and braces: the drawing overlay must not eat the
        // taps on this panel (the ball tap already turned it
        // off - this covers the mini ball path too).
        ScreenRecordService.setDrawMode(this, false)
        activeTool = ScreenRecordService.currentTool
        setContent {
            CalNyxTheme {
                val colorScheme = MaterialTheme.colorScheme
                // Elapsed time, 1 / sec. If the recording ends
                // elsewhere (notification Stop, system chip),
                // close this panel by itself.
                LaunchedEffect(Unit) {
                    while (true) {
                        if (!ScreenRecordService.isRecording) {
                            finish()
                            return@LaunchedEffect
                        }
                        elapsedSec =
                            (System.currentTimeMillis() - ScreenRecordService.startedAtMillis) / 1000
                        delay(1000)
                    }
                }
                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val minDim = minOf(maxWidth, maxHeight)
                    val cornerCard = minDim * 0.078f
                    val fTitle = (minDim * 0.047f).value.sp
                    val fBody = (minDim * 0.044f).value.sp
                    val gap = minDim * 0.022f
                    val cardPad = minDim * 0.033f
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f))
                            .clickable { finish() },
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            // Consume taps on the card so they do
                            // not fall through to the dim background
                            // (which closes the panel).
                            modifier = Modifier.fillMaxWidth(0.88f).clickable {},
                            shape = RoundedCornerShape(cornerCard),
                            colors = CardDefaults.cardColors(
                                containerColor = colorScheme.surfaceContainer
                            ),
                            border = BorderStroke(1.dp, colorScheme.outline)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                                    .padding(cardPad),
                                verticalArrangement = Arrangement.spacedBy(gap),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Header: red dot + Recording + time.
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(gap)
                                ) {
                                    Box(
                                        modifier = Modifier.size(minDim * 0.03f)
                                            .clip(CircleShape)
                                            .background(CalNyxRed)
                                    )
                                    Text(
                                        "Recording",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = fTitle,
                                        color = colorScheme.onBackground
                                    )
                                    Text(
                                        formatElapsed(elapsedSec),
                                        fontSize = fBody,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                }
                                // Tools, 3 per row. Same icon-circle
                                // style as the Hardcore cards: icon in
                                // a circle, label under it. The active
                                // tool is filled (inverted).
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(gap)
                                ) {
                                    ToolButton(
                                        label = "Pen",
                                        icon = Icons.Default.Edit,
                                        selected = activeTool == DrawTool.PEN,
                                        minDim = minDim,
                                        modifier = Modifier.weight(1f),
                                        onClick = { onToolClick(DrawTool.PEN) }
                                    )
                                    ToolButton(
                                        label = "Circle",
                                        icon = Icons.Default.RadioButtonUnchecked,
                                        selected = activeTool == DrawTool.CIRCLE,
                                        minDim = minDim,
                                        modifier = Modifier.weight(1f),
                                        onClick = { onToolClick(DrawTool.CIRCLE) }
                                    )
                                    ToolButton(
                                        label = "Square",
                                        icon = Icons.Default.CropSquare,
                                        selected = activeTool == DrawTool.RECT,
                                        minDim = minDim,
                                        modifier = Modifier.weight(1f),
                                        onClick = { onToolClick(DrawTool.RECT) }
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(gap)
                                ) {
                                    ToolButton(
                                        label = "Triangle",
                                        icon = Icons.Default.ChangeHistory,
                                        selected = activeTool == DrawTool.TRIANGLE,
                                        minDim = minDim,
                                        modifier = Modifier.weight(1f),
                                        onClick = { onToolClick(DrawTool.TRIANGLE) }
                                    )
                                    ToolButton(
                                        label = "Clear",
                                        icon = Icons.Default.DeleteSweep,
                                        minDim = minDim,
                                        modifier = Modifier.weight(1f),
                                        onClick = { onClearClick() }
                                    )
                                    ToolButton(
                                        label = "Stop",
                                        icon = Icons.Default.Stop,
                                        danger = true,
                                        minDim = minDim,
                                        modifier = Modifier.weight(1f),
                                        onClick = { onStopClick() }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Pick a tool (closes the panel so the user can draw at
    // once) or turn the active tool off again.
    private fun onToolClick(tool: DrawTool) {
        if (tool == ScreenRecordService.currentTool) {
            ScreenRecordService.setTool(this, DrawTool.NONE)
        } else {
            ScreenRecordService.setTool(this, tool)
        }
        activeTool = ScreenRecordService.currentTool
        finish()
    }

    // Clear wipes every mark. The panel STAYS open - clear,
    // then pick the next tool.
    private fun onClearClick() {
        ScreenRecordService.clearMarks(this)
    }

    private fun onStopClick() {
        ScreenRecordService.stop(this)
        finish()
    }
}

@Composable
private fun ToolButton(
    label: String,
    icon: ImageVector,
    minDim: Dp,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val circleBg = when {
        selected -> colorScheme.onBackground
        else -> colorScheme.surfaceVariant
    }
    val iconTint = when {
        danger -> CalNyxRed
        selected -> colorScheme.background
        else -> colorScheme.onBackground
    }
    val labelColor = when {
        danger -> CalNyxRed
        selected -> colorScheme.onBackground
        else -> colorScheme.onBackground
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(minDim * 0.044f))
            .clickable { onClick() }
            .padding(vertical = minDim * 0.022f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(minDim * 0.014f)
    ) {
        Box(
            modifier = Modifier.size(minDim * 0.133f)
                .clip(CircleShape)
                .background(circleBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = label,
                modifier = Modifier.size(minDim * 0.067f),
                tint = iconTint
            )
        }
        Text(
            label,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            fontSize = (minDim * 0.033f).value.sp,
            color = labelColor,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

private fun formatElapsed(totalSec: Long): String {
    val m = totalSec / 60
    val s = totalSec % 60
    return "%02d:%02d".format(m, s)
}

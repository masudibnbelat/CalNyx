// FILE: app/src/main/java/com/calnyx/app/BlueFilm/ScreenRecordToolbarActivity.kt
// NEW FILE - recording toolbar as a transparent Activity (2026-11-20).
//
// Opens when the floating ball (or the mini ball) is tapped while
// a screen recording runs. Same pattern as BallPanelActivity:
// translucent, its own task, never in recents, tap the dim
// background or Back to close - the recording keeps running.
//
// Tools in a 3 x 3 grid of BIG icons, no text labels:
//   Pen, Circle, Rectangle / Arrow, Clear, Emoji /
//   Color, Stop, Screenshot. The grid follows the ball
//   panel exactly: same card size, same circle buttons
//   (a light circle under each icon), same background -
//   only the colours come from the theme variables, so
//   the card is the theme's background and the circles
//   are surfaceContainer. The active tool is filled
//   (inverted). Color opens a 360-degree hue wheel - tap or drag to pick a
//   colour (red by default, the choice is saved, and each
//   mark keeps the colour it was drawn with). Screenshot
//   takes the phone's official screenshot (the toolbar and
//   the ball are hidden first, like the ball panel's
//   button). Tapping a tool selects it AND closes the panel,
//   so the user can draw at once; tapping the active tool
//   again turns it off. Emoji opens a sticker picker first -
//   tapping a sticker selects it and closes. Clear wipes
//   all marks AND turns the tool off, so the screen is
//   normal again. Stop ends the recording and closes.
// The header shows a red dot + the elapsed time (1 / sec); if
// the recording ends elsewhere, the panel closes itself.
package com.calnyx.app.bluefilm

import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CropLandscape
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calnyx.app.applock.AppLockService
import com.calnyx.app.ball.FloatingBallService
import com.calnyx.app.ui.theme.CalNyxRed
import com.calnyx.app.ui.theme.CalNyxTheme
import kotlinx.coroutines.delay
import kotlin.math.atan2
import kotlin.math.hypot

class ScreenRecordToolbarActivity : ComponentActivity() {

    private var elapsedSec by mutableStateOf(0L)
    private var activeTool by mutableStateOf(DrawTool.NONE)
    private var showEmojiPicker by mutableStateOf(false)
    private var showColorPicker by mutableStateOf(false)
    // The drawing colour, as an ARGB Int (what the service
    // stores). Read from the saved colour on open; red by
    // default, even if no colour was ever picked.
    private var selectedColor by mutableStateOf(0)
    // The wheel's current hue (0..360), kept while the picker
    // is open. Set from the saved colour when the picker opens.
    private var pickerHue by mutableStateOf(0f)
    private val mainHandler = Handler(Looper.getMainLooper())

    // The stickers the picker offers. One tap stamps it.
    private val stickers = listOf(
        "😀", "😂", "😍", "👍",
        "👏", "❤️", "🔥", "⭐",
        "🎉", "😮", "😢", "🙏"
    )

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
        selectedColor = ScreenRecordService.savedColor(this)
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
                    val cornerCard = minDim * 0.071f
                    val fTitle = (minDim * 0.047f).value.sp
                    val fBody = (minDim * 0.044f).value.sp
                    val gap = minDim * 0.016f
                    val cardPad = minDim * 0.031f
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f))
                            .clickable { finish() },
                        contentAlignment = Alignment.Center
                    ) {
                        // Same construction as the ball panel's
                        // card (a Surface, not a Card), and the
                        // same tint: the card colour is the
                        // picture's colour - background + the
                        // tonal tint, which makes it the lighter
                        // grey (measured #333333) instead of flat
                        // background. Both windows use the same
                        // Surface with the same tint, so they
                        // cannot differ. NO shadow (the user wants
                        // the flat look from the picture): the
                        // shadow is 0, only the tint stays.
                        Surface(
                            // Consume taps on the card so they do
                            // not fall through to the dim background
                            // (which closes the panel).
                            modifier = Modifier.fillMaxWidth(0.82f).clickable {},
                            shape = RoundedCornerShape(cornerCard),
                            color = colorScheme.background,
                            contentColor = colorScheme.onBackground,
                            tonalElevation = minDim * 0.02f,
                            shadowElevation = 0.dp
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                                    .padding(cardPad),
                                verticalArrangement = Arrangement.spacedBy(minDim * 0.010f),
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
                                if (showEmojiPicker) {
                                    // Sticker picker: tap one to
                                    // stamp it on the screen.
                                    Text(
                                        "Stickers",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = fTitle,
                                        color = colorScheme.onBackground
                                    )
                                    for (row in stickers.chunked(4)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceEvenly
                                        ) {
                                            for (sticker in row) {
                                                Text(
                                                    sticker,
                                                    fontSize = (minDim * 0.1f).value.sp,
                                                    modifier = Modifier.clickable { onEmojiPicked(sticker) }
                                                        .padding(minDim * 0.011f)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        "‹ Tools",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = fBody,
                                        color = colorScheme.onSurfaceVariant,
                                        modifier = Modifier.clickable { showEmojiPicker = false }
                                            .padding(vertical = minDim * 0.011f)
                                    )
                                } else if (showColorPicker) {
                                    // Colour wheel: a 360-degree hue
                                    // wheel. Tap or drag on the ring to
                                    // pick; the middle shows the picked
                                    // colour. "‹ Tools" goes back to
                                    // the grid.
                                    Text(
                                        "Color",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = fTitle,
                                        color = colorScheme.onBackground
                                    )
                                    ColorWheel(
                                        hue = pickerHue,
                                        minDim = minDim,
                                        onHueChanged = { pickerHue = it },
                                        onHuePicked = {
                                            onColorClick(Color.hsv(it, 1f, 1f).toArgb())
                                        }
                                    )
                                    Text(
                                        "‹ Tools",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = fBody,
                                        color = colorScheme.onSurfaceVariant,
                                        modifier = Modifier.clickable { showColorPicker = false }
                                            .padding(vertical = minDim * 0.011f)
                                    )
                                } else {
                                    // Tools: a 3 x 3 grid of big icons,
                                    // no text labels. Row 1 Pen,
                                    // Circle, Rectangle; Row 2 Arrow,
                                    // Clear, Emoji; Row 3 Color, Stop,
                                    // Screenshot. Circles, like the ball
                                    // panel - the active tool is filled
                                    // (inverted).
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        ToolButton(
                                            label = "Pen",
                                            icon = Icons.Default.Edit,
                                            selected = activeTool == DrawTool.PEN,
                                            minDim = minDim,
                                            onClick = { onToolClick(DrawTool.PEN) }
                                        )
                                        ToolButton(
                                            label = "Circle",
                                            icon = Icons.Default.RadioButtonUnchecked,
                                            selected = activeTool == DrawTool.CIRCLE,
                                            minDim = minDim,
                                            onClick = { onToolClick(DrawTool.CIRCLE) }
                                        )
                                        ToolButton(
                                            label = "Rectangle",
                                            icon = Icons.Default.CropLandscape,
                                            selected = activeTool == DrawTool.RECT,
                                            minDim = minDim,
                                            onClick = { onToolClick(DrawTool.RECT) }
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        ToolButton(
                                            label = "Arrow",
                                            icon = Icons.Default.ArrowForward,
                                            selected = activeTool == DrawTool.ARROW,
                                            minDim = minDim,
                                            onClick = { onToolClick(DrawTool.ARROW) }
                                        )
                                        ToolButton(
                                            label = "Clear",
                                            icon = Icons.Default.DeleteSweep,
                                            minDim = minDim,
                                            onClick = { onClearClick() }
                                        )
                                        ToolButton(
                                            label = "Emoji",
                                            icon = Icons.Default.EmojiEmotions,
                                            selected = activeTool == DrawTool.EMOJI,
                                            minDim = minDim,
                                            onClick = { onEmojiClick() }
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        ToolButton(
                                            label = "Color",
                                            icon = Icons.Default.Palette,
                                            minDim = minDim,
                                            onClick = { onColorButtonClick() }
                                        )
                                        ToolButton(
                                            label = "Stop",
                                            icon = Icons.Default.Stop,
                                            danger = true,
                                            minDim = minDim,
                                            onClick = { onStopClick() }
                                        )
                                        ToolButton(
                                            label = "Screenshot",
                                            icon = ScreenshotIcon,
                                            minDim = minDim,
                                            onClick = { onScreenshotClick() }
                                        )
                                    }
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

    // Emoji button: already active -> turn it off (like the
    // other tools). Otherwise open the sticker picker.
    private fun onEmojiClick() {
        if (ScreenRecordService.currentTool == DrawTool.EMOJI) {
            ScreenRecordService.setTool(this, DrawTool.NONE)
            activeTool = DrawTool.NONE
            finish()
        } else {
            showEmojiPicker = true
        }
    }

    // A sticker was picked: remember it, arm the stamp tool,
    // close the panel - tap the screen to stamp it.
    private fun onEmojiPicked(emoji: String) {
        ScreenRecordService.setEmoji(this, emoji)
        ScreenRecordService.setTool(this, DrawTool.EMOJI)
        activeTool = DrawTool.EMOJI
        finish()
    }

    // Clear wipes every mark AND turns the active tool off, so
    // the screen is normal again. The panel STAYS open - clear,
    // then pick the next tool.
    private fun onClearClick() {
        ScreenRecordService.clearMarks(this)
        ScreenRecordService.setTool(this, DrawTool.NONE)
        activeTool = DrawTool.NONE
    }

    // A colour was picked: remember it (saved) and keep the
    // panel open, so a tool can be picked right after.
    private fun onColorClick(color: Int) {
        ScreenRecordService.setColor(this, color)
        selectedColor = color
    }

    // The Color button: open the hue wheel, starting from the
    // colour already picked (red if none was ever picked).
    private fun onColorButtonClick() {
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(selectedColor, hsv)
        pickerHue = hsv[0]
        showColorPicker = true
    }

    // Screenshot from the toolbar: same as the ball panel's
    // button - hide the ball for 2 s, close this toolbar, then
    // the phone's OFFICIAL screenshot. The toolbar and the
    // ball are gone by the time it fires, so neither is in
    // the picture.
    private fun onScreenshotClick() {
        FloatingBallService.hide(this, 2000)
        finish()
        mainHandler.postDelayed({
            if (!AppLockService.requestOfficialScreenshot()) {
                Toast.makeText(
                    this,
                    "Turn on Accessibility for CalNyx first.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }, 400)
    }

    private fun onStopClick() {
        ScreenRecordService.stop(this)
        finish()
    }
}

// One grid button, in the ball panel's style: a big icon
// in a circle, no text label (the label is only the
// accessibility description). The circle is a light
// background under the icon, like the panel's thumb
// circles. The active tool is filled (inverted); Stop's
// icon is red.
@Composable
private fun ToolButton(
    label: String,
    icon: ImageVector,
    minDim: Dp,
    selected: Boolean = false,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val buttonBg = when {
        selected -> colorScheme.onBackground
        else -> colorScheme.surfaceContainer
    }
    val iconTint = when {
        danger -> CalNyxRed
        selected -> colorScheme.background
        else -> colorScheme.onBackground
    }
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .clickable { onClick() }
            .padding(minDim * 0.010f),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(minDim * 0.143f)
                .clip(CircleShape)
                .background(buttonBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = label,
                modifier = Modifier.size(minDim * 0.092f),
                tint = iconTint
            )
        }
    }
}

// A simple 360-degree hue wheel: the ring shows every hue,
// the middle shows the picked colour. Tap or drag on the
// ring to pick; the colour is committed when the finger
// lifts (onHuePicked), so dragging does not save on every
// pixel. Taps inside the middle preview are ignored (no
// hue there to pick).
@Composable
private fun ColorWheel(
    hue: Float,
    minDim: Dp,
    onHueChanged: (Float) -> Unit,
    onHuePicked: (Float) -> Unit
) {
    // The latest hue, for the drag-end commit - the gesture
    // block below is keyed on Unit, so it would otherwise
    // see only the hue from when it was first set up.
    val latestHue by rememberUpdatedState(hue)
    val wheelColors = listOf(
        Color.Red, Color.Yellow, Color.Green, Color.Cyan,
        Color.Blue, Color.Magenta, Color.Red
    )
    Canvas(
        modifier = Modifier
            .size(minDim * 0.55f)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        hueAt(offset, size)?.let {
                            onHueChanged(it)
                            onHuePicked(it)
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        hueAt(offset, size)?.let { onHueChanged(it) }
                    },
                    onDrag = { change, _ ->
                        hueAt(change.position, size)?.let { onHueChanged(it) }
                    },
                    onDragEnd = { onHuePicked(latestHue) }
                )
            }
    ) {
        val radius = size.minDimension / 2f
        drawCircle(
            brush = Brush.sweepGradient(wheelColors),
            radius = radius,
            center = center
        )
        drawCircle(
            color = Color.hsv(hue, 1f, 1f),
            radius = radius * 0.45f,
            center = center
        )
    }
}

// The hue (0..360) at a point on the wheel, or null when the
// point is inside the middle preview or outside the wheel.
// The sweep starts at 3 o'clock, clockwise - the same order
// as the wheel's colours.
private fun hueAt(offset: Offset, size: IntSize): Float? {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val dx = offset.x - cx
    val dy = offset.y - cy
    val r = hypot(dx, dy)
    // IntSize has no minDimension in this project's Compose
    // version, so take the smaller side directly.
    val outer = minOf(size.width, size.height) / 2f
    if (r < outer * 0.45f || r > outer) return null
    val angle = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
    return (angle + 360f) % 360f
}

// The screenshot icon, traced from the user's image (same
// vector as the ball panel's Screenshot button): a dashed
// capture frame - a + at top-left and bottom-right, a rounded
// corner at top-right and bottom-left, a dash mid each side.
// Drawn black like every Material icon; the button's tint
// turns it white on dark backgrounds.
private val ScreenshotIcon: ImageVector = ImageVector.Builder(
    name = "Screenshot",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).addPath(
    pathData = listOf(
        // Top-left + : horizontal, then vertical.
        PathNode.MoveTo(0.9f, 4.0f),
        PathNode.LineTo(7.0f, 4.0f),
        PathNode.MoveTo(4.0f, 0.9f),
        PathNode.LineTo(4.0f, 7.0f),
        // Top dash.
        PathNode.MoveTo(10.75f, 3.75f),
        PathNode.LineTo(13.25f, 3.75f),
        // Top-right rounded corner.
        PathNode.MoveTo(16.9f, 3.75f),
        PathNode.LineTo(18.9f, 3.75f),
        PathNode.QuadTo(20.2f, 3.75f, 20.2f, 5.05f),
        PathNode.LineTo(20.2f, 7.0f),
        // Left dash.
        PathNode.MoveTo(3.7f, 10.8f),
        PathNode.LineTo(3.7f, 13.2f),
        // Right dash.
        PathNode.MoveTo(20.2f, 10.8f),
        PathNode.LineTo(20.2f, 13.2f),
        // Bottom-left rounded corner.
        PathNode.MoveTo(3.7f, 17.0f),
        PathNode.LineTo(3.7f, 18.9f),
        PathNode.QuadTo(3.7f, 20.2f, 5.0f, 20.2f),
        PathNode.LineTo(7.0f, 20.2f),
        // Bottom dash.
        PathNode.MoveTo(10.75f, 20.2f),
        PathNode.LineTo(13.25f, 20.2f),
        // Bottom-right + : horizontal, then vertical.
        PathNode.MoveTo(17.0f, 20.0f),
        PathNode.LineTo(23.1f, 20.0f),
        PathNode.MoveTo(20.0f, 17.0f),
        PathNode.LineTo(20.0f, 23.1f)
    ),
    stroke = SolidColor(Color.Black),
    strokeLineWidth = 1.8f,
    strokeLineCap = StrokeCap.Round,
    strokeLineJoin = StrokeJoin.Round
).build()

private fun formatElapsed(totalSec: Long): String {
    val m = totalSec / 60
    val s = totalSec % 60
    return "%02d:%02d".format(m, s)
}

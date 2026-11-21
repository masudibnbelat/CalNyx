// FILE: app/src/main/java/com/calnyx/app/thirdeye/ThirdEyeScreen.kt
// NEW FILE - the "Third Eye" hub drawer screen (2026-11-21).
// Lives in the NEW thirdeye/ folder (after the rename script moved
// the recorder to spy/ and Screen Stream here from network/).
// The hub holds, in this order:
//   1. Screen Share (ScreenStreamScreen) - Share on top, Watch below
//   2. Camera Live (CameraLiveScreen) - Share my camera, Watch a camera
// Colours come from the theme (Color.kt) only, all sizes are
// % of the window, English only, 1.dp borders only.
// Leftward swipe anywhere opens the drawer (universal rule).
package com.calnyx.app.thirdeye

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsControllerCompat

@Composable
fun ThirdEyeScreen(
    darkTheme: Boolean,
    onOpenDrawer: () -> Unit = {}
) {
    // All colours come from the theme (Color.kt). darkTheme is used
    // below for the status / navigation bar icon colours.
    val colorScheme = MaterialTheme.colorScheme
    val pageBg = colorScheme.background
    val onPageColor = colorScheme.onBackground
    val cardBg = colorScheme.surfaceContainer
    val cardBorder = colorScheme.outline
    val thumbBg = colorScheme.surfaceVariant

    // Status bar + navigation bar match the page background:
    // same fix as the live NetworkScreen / recorder.
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            window.statusBarColor = pageBg.toArgb()
            window.navigationBarColor = pageBg.toArgb()
            val controller = WindowInsetsControllerCompat(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
            .background(pageBg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        val screenW = maxWidth
        val screenH = maxHeight
        val minDim = minOf(screenW, screenH)

        val fTitle = (minDim * 0.055f).value.sp
        val fSmall = (minDim * 0.032f).value.sp
        val padX = screenW * 0.05f
        val padY = screenH * 0.02f
        val gap = screenH * 0.02f
        val labelGap = minDim * 0.008f
        val fLabel = (minDim * 0.032f).value.sp
        val iconBox = screenW * 0.13f

        // DRAWER SWIPE (universal rule, 2026-11-06): a LEFTWARD
        // drag anywhere on this screen opens the app drawer.
        // Rightward drags are not consumed here. A tap (no real
        // movement) does nothing, so buttons still work.
        // Threshold is 6.7% of minDim; keyed on minDim so the
        // detector restarts with a new threshold on resize.
        val drawerSwipe = Modifier.pointerInput(minDim) {
            val thresholdPx = (minDim * 0.067f).toPx()
            var totalDx = 0f
            var done = false
            detectHorizontalDragGestures(
                onDragStart = { totalDx = 0f; done = false },
                onDragEnd = { totalDx = 0f; done = false },
                onDragCancel = { totalDx = 0f; done = false },
                onHorizontalDrag = { change, dragAmount ->
                    if (done) return@detectHorizontalDragGestures
                    totalDx += dragAmount
                    if (totalDx < -thresholdPx) {
                        done = true
                        onOpenDrawer()
                        change.consume()
                    }
                }
            )
        }

        Column(
            modifier = Modifier.fillMaxSize()
                .then(drawerSwipe)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = padX, vertical = padY),
            verticalArrangement = Arrangement.spacedBy(gap)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(iconBox)
                        .clip(CircleShape)
                        .background(cardBg)
                        .border(1.dp, cardBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Visibility,
                        contentDescription = null,
                        tint = onPageColor,
                        modifier = Modifier.size(iconBox * 0.55f)
                    )
                }
                Spacer(modifier = Modifier.width(screenW * 0.03f))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(minDim * 0.004f)
                ) {
                    Text(
                        "Third Eye",
                        fontWeight = FontWeight.Bold,
                        fontSize = fTitle,
                        color = onPageColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "Camera Live and Screen Share",
                        fontSize = fSmall,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 1. Screen Share ON TOP (user's order). The Share-first
            // card order inside ScreenStreamScreen comes from
            // Task B's live ScreenStreamScreen.kt - apply it too.
            Column(
                verticalArrangement = Arrangement.spacedBy(labelGap)
            ) {
                Text(
                    "SCREEN SHARE",
                    fontWeight = FontWeight.Medium,
                    fontSize = fLabel,
                    letterSpacing = (minDim * 0.006f).value.sp,
                    color = colorScheme.onSurfaceVariant
                )
                ScreenStreamScreen(
                    cardBg = cardBg,
                    cardBorder = cardBorder,
                    onPageColor = onPageColor,
                    thumbBg = thumbBg,
                    minDim = minDim
                )
            }

            // 2. Camera Live BELOW it - with a clear break between
            // the two sections: a bigger gap plus a thin divider,
            // so Screen Share and Camera Live read as two separate
            // sections, not one long list of similar cards.
            Box(
                modifier = Modifier.fillMaxWidth().height(1.dp).background(cardBorder)
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(labelGap)
            ) {
                Text(
                    "CAMERA LIVE",
                    fontWeight = FontWeight.Medium,
                    fontSize = fLabel,
                    letterSpacing = (minDim * 0.006f).value.sp,
                    color = colorScheme.onSurfaceVariant
                )
                CameraLiveScreen(
                    cardBg = cardBg,
                    cardBorder = cardBorder,
                    onPageColor = onPageColor,
                    thumbBg = thumbBg,
                    minDim = minDim
                )
            }

            Spacer(modifier = Modifier.height(screenH * 0.01f))
        }
    }
}

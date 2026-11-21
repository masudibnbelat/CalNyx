// FILE: app/src/main/java/com/calnyx/app/ui/screens/NetworkScreen.kt

// NETWORK - NEW SCREEN (2026-11-20).
//  Split out of MoanControlScreen.kt (2026-11-20): Hardcore
//  (Hardware) in HardcoreScreen.kt, Moan (Sound) in MoanScreen.kt,
//  and Network here. DELETE MoanControlScreen.kt from the project,
//  or the HardcoreScreen / MoanScreen declarations will exist
//  twice and the build will fail.
//  Two sections now:
//  1. Network Switcher - in NetworkSwitcher.kt (same folder).
//  2. Open in WhatsApp (Click to Chat) - type a number to open
//     its WhatsApp chat directly (WhatsAppHelper normalizes it:
//     01XXXXXXXXX -> 8801XXXXXXXXX).
// NETWORK SWITCHER SPLIT (2026-11-21): the Network Switcher
// card + openPhoneInfo moved to NetworkSwitcher.kt. This file
// keeps the page, the header, Open in WhatsApp, and the Screen
// Stream section call. Nothing else changed.
// THEME VARIABLES (2026-11-20): every colour comes from the
//  theme (Color.kt) through MaterialTheme.colorScheme. Status-bar
//  fix and drawer swipe are the same as Hardcore / Moan.
//  % SIZES: everything is a % of minDim / windowW / windowH,
//  like the other screens - no fixed dp/sp except 1.dp borders.
package com.calnyx.app.network

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsControllerCompat
import com.calnyx.app.R
import com.calnyx.app.network.WhatsAppHelper
import com.calnyx.app.hardcore.HardcoreScreen
import com.calnyx.app.moan.MoanScreen

@Composable
fun NetworkScreen(
    darkTheme: Boolean,
    onOpenDrawer: () -> Unit = {}
) {
    // All colours come from the theme (Color.kt) - see the note at
    // the top of this file. darkTheme is still used below for the
    // status / navigation bar icon colours.
    val colorScheme = MaterialTheme.colorScheme
    val pageBg = colorScheme.background
    val cardBg = colorScheme.surfaceContainer
    val cardBorder = colorScheme.outline
    val onPageColor = colorScheme.onBackground
    val thumbBg = colorScheme.surfaceVariant
    var note by remember { mutableStateOf<String?>(null) }
    // WhatsApp number input (digits only).
    var waNumber by remember { mutableStateOf("") }
    // STATUS BAR + NAVIGATION BAR match the page background: the
    // top strip (clock, battery, WiFi, network) must be the same
    // colour as the page below it, in both themes — it was showing
    // the system default (white in light mode) instead of the page
    // colour. Same fix as Blue Film V8 / Moan V2.
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
    // DRAWER SWIPE (universal rule, 2026-11-06): a LEFTWARD
    // drag anywhere on this screen opens the app drawer.
    // Rightward drags are not consumed here. A tap (no real
    // movement) does nothing, so buttons still work.
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val windowW = maxWidth
        val windowH = maxHeight
        val minDim = minOf(windowW, windowH)
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

    Surface(modifier = Modifier.fillMaxSize(), color = pageBg, contentColor = onPageColor) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().then(drawerSwipe)) {
            if (note != null) {
                Text(
                    text = note ?: "",
                    fontSize = (minDim * 0.036f).value.sp,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = windowW * 0.044f, vertical = windowH * 0.01f)
                )
            }
            NetworkContent(
                cardBg = cardBg,
                cardBorder = cardBorder,
                onPageColor = onPageColor,
                thumbBg = thumbBg,
                minDim = minDim,
                waNumber = waNumber,
                onWaNumberChange = { waNumber = it },
                onNote = { note = it }
            )
        }
    }
    }
}

@Composable
private fun NetworkContent(
    cardBg: Color,
    cardBorder: Color,
    onPageColor: Color,
    thumbBg: Color,
    minDim: Dp,
    waNumber: String,
    onWaNumberChange: (String) -> Unit,
    onNote: (String?) -> Unit
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val scroll = rememberScrollState()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(start = minDim * 0.033f, end = minDim * 0.033f, bottom = minDim * 0.033f),
        verticalArrangement = Arrangement.spacedBy(minDim * 0.022f)
    ) {
        // HEADER - big title with icon, like Hardware / Sound.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(minDim * 0.144f).clip(CircleShape).background(thumbBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.SignalCellularAlt, contentDescription = null, modifier = Modifier.size(minDim * 0.078f), tint = onPageColor)
            }
            Spacer(modifier = Modifier.width(minDim * 0.033f))
            Text("Network", fontWeight = FontWeight.Bold, fontSize = (minDim * 0.078f).value.sp, color = onPageColor)
        }
        // NETWORK SWITCHER - now in its own file (NetworkSwitcher.kt,
        // same folder). Same card, same behaviour.
        NetworkSwitcherCard(
            cardBg = cardBg,
            cardBorder = cardBorder,
            onPageColor = onPageColor,
            thumbBg = thumbBg,
            minDim = minDim,
            onNote = onNote
        )
        // OPEN IN WHATSAPP (Click to Chat) - big Material 3 card.
        // Type a number to open its WhatsApp chat directly. The
        // input has a proper label, the WhatsApp logo as its
        // leading icon and a clear (X) button when text is typed;
        // below it a full-width "Add to WhatsApp" button with a
        // Send icon opens the chat. The logo is the real WhatsApp
        // bubble (res/drawable/ic_whatsapp.xml), tinted with the
        // theme colour - black / white only, no WhatsApp green,
        // per the app's palette rule.
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(minDim * 0.044f),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            border = BorderStroke(1.dp, cardBorder)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(minDim * 0.044f),
                verticalArrangement = Arrangement.spacedBy(minDim * 0.022f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(minDim * 0.122f).clip(CircleShape).background(thumbBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_whatsapp),
                            contentDescription = null,
                            modifier = Modifier.size(minDim * 0.061f),
                            tint = onPageColor
                        )
                    }
                    Spacer(modifier = Modifier.width(minDim * 0.033f))
                    Text("Open in WhatsApp", fontWeight = FontWeight.SemiBold, fontSize = (minDim * 0.05f).value.sp, color = onPageColor, modifier = Modifier.weight(1f))
                }
                Text(
                    "Type a number to open its WhatsApp chat directly.",
                    fontSize = (minDim * 0.039f).value.sp,
                    color = colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = waNumber,
                    onValueChange = { onWaNumberChange(it.filter { ch -> ch.isDigit() }) },
                    label = { Text("Phone number") },
                    placeholder = { Text("01XXXXXXXXX") },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_whatsapp),
                            contentDescription = null,
                            modifier = Modifier.size(minDim * 0.061f)
                        )
                    },
                    trailingIcon = {
                        if (waNumber.isNotEmpty()) {
                            IconButton(onClick = { onWaNumberChange("") }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Clear number",
                                    modifier = Modifier.size(minDim * 0.056f)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().height(minDim * 0.167f),
                    shape = RoundedCornerShape(minDim * 0.033f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = onPageColor,
                        unfocusedBorderColor = cardBorder,
                        cursorColor = onPageColor
                    )
                )
                Button(
                    onClick = {
                        if (WhatsAppHelper.openChat(context, waNumber)) {
                            onNote(null)
                        } else {
                            onNote("Type a valid number first.")
                        }
                    },
                    enabled = waNumber.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(minDim * 0.144f),
                    shape = RoundedCornerShape(minDim * 0.033f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = onPageColor,
                        contentColor = cardBg
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = null,
                        modifier = Modifier.size(minDim * 0.056f)
                    )
                    Spacer(modifier = Modifier.width(minDim * 0.022f))
                    Text(
                        "Add to WhatsApp",
                        fontSize = (minDim * 0.044f).value.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(minDim * 0.022f))
    }
}



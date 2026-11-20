// FILE: app/src/main/java/com/calnyx/app/ui/screens/NetworkScreen.kt
// NETWORK - NEW SCREEN (2026-11-20).
//  Split out of MoanControlScreen.kt (2026-11-20): Hardcore
//  (Hardware) in HardcoreScreen.kt, Moan (Sound) in MoanScreen.kt,
//  and Network here. DELETE MoanControlScreen.kt from the project,
//  or the HardcoreScreen / MoanScreen declarations will exist
//  twice and the build will fail.
//  Two big Material 3 cards, nothing else:
//  1. Network Switcher - opens the system Phone info (Testing)
//     screen to switch 2G / 3G / 4G / 5G (openPhoneInfo below,
//     moved here unchanged from MoanControlScreen).
//  2. Open in WhatsApp (Click to Chat) - type a number to open
//     its WhatsApp chat directly (WhatsAppHelper normalizes it:
//     01XXXXXXXXX -> 8801XXXXXXXXX).
// THEME VARIABLES (2026-11-20): every colour comes from the
//  theme (Color.kt) through MaterialTheme.colorScheme. Status-bar
//  fix and drawer swipe are the same as Hardcore / Moan.
//  % SIZES: everything is a % of minDim / windowW / windowH,
//  like the other screens - no fixed dp/sp except 1.dp borders.
package com.calnyx.app.ui.screens

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import com.calnyx.app.moan.WhatsAppHelper

private fun tryStart(context: Context, intent: Intent): Boolean {
    return try {
        context.startActivity(intent)
        true
    } catch (_: Exception) {
        false
    }
}

// Open the system Phone info (Testing) screen.
// Returns: 0 = Phone info opened, 1 = secret-code broadcast
// sent (Phone info should open), 2 = dialer opened with the
// *#*#4636#*#* code (user taps Call, Phone info opens),
// 3 = Mobile Network Settings (Testing blocked on this phone),
// 4 = Settings, -1 = nothing worked.
// The target list is the reference app's method: RadioInfo
// then TestingSettings, in com.android.settings first and
// then com.android.phone. The com.android.phone package is
// the part CalNyx was missing - on some phones (including
// this realme) the screen lives in com.android.phone, not
// com.android.settings, so the settings-only attempts all
// failed. After the four targets come the OEM inner-class
// names, then the secret-code broadcast (the SAME broadcast
// the dialer sends when you type *#*#4636#*#* by hand),
// then the dialer with the code, then Mobile Network
// Settings, then Settings.
// ACTION_CALL is DELIBERATELY NOT used: on this realme it
// does NOT open Phone info - it places a REAL call to
// *#*#4636#*#* (SIM picker, then "Calling..."). The MMI
// code is only intercepted when typed in the dialer by hand,
// not when dialed via an intent.
private fun openPhoneInfo(context: Context): Int {
    // 1-4. RadioInfo / TestingSettings, in both packages.
    // No action, just the component + NEW_TASK - the same
    // shape the reference app uses.
    val targets = listOf(
        ComponentName("com.android.settings", "com.android.settings.RadioInfo"),
        ComponentName("com.android.phone", "com.android.phone.settings.RadioInfo"),
        ComponentName("com.android.settings", "com.android.settings.TestingSettings"),
        ComponentName("com.android.phone", "com.android.phone.settings.TestingSettings")
    )
    for (target in targets) {
        try {
            val intent = Intent().apply {
                component = target
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            return 0
        } catch (_: Exception) {
        }
    }
    // 5-6. OEM inner-class names. Some manufacturers host the
    // Testing / RadioInfo screen as a fragment inside the
    // Settings activity (Settings$...Activity). These class
    // names do not exist on stock Android, so they fail
    // silently there and fall through - no harm.
    val innerTargets = listOf(
        ComponentName("com.android.settings", "com.android.settings.Settings\$TestingSettingsActivity"),
        ComponentName("com.android.settings", "com.android.settings.Settings\$RadioInfoActivity")
    )
    for (target in innerTargets) {
        try {
            val intent = Intent().apply {
                component = target
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            return 0
        } catch (_: Exception) {
        }
    }
    // 7. Secret-code broadcast - direct, no dialer, no call.
    try {
        val secret = Intent("android.provider.Telephony.SECRET_CODE").apply {
            data = Uri.parse("android_secret_code://4636")
        }
        context.sendBroadcast(secret)
        return 1
    } catch (_: Exception) {
        // Blocked on this phone - fall through to the dialer.
    }
    // 8. Dialer code *#*#4636#*#* - opens the dialer with the
    // code filled in; tap Call and Phone info opens.
    val dial = Intent(
        Intent.ACTION_DIAL,
        Uri.parse("tel:*%23*%234636%23*%23*")
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    if (tryStart(context, dial)) return 2
    // 9. Mobile Network Settings (Testing blocked).
    val net = Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    if (tryStart(context, net)) return 3
    // 10. Settings.
    val settings = Intent(Settings.ACTION_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    if (tryStart(context, settings)) return 4
    return -1
}

// Runs openPhoneInfo and tells the user what happened.
private fun openNetworkSwitcher(
    context: Context,
    onNote: (String?) -> Unit
) {
    when (openPhoneInfo(context)) {
        0 -> onNote(null)
        1 -> onNote(null)
        2 -> onNote("Dialer opened with *#*#4636#*#* - tap Call and Phone info will open.")
        3 -> onNote("Phone info is blocked on this phone - Mobile Network Settings opened instead.")
        4 -> onNote("Phone info could not be opened - Settings opened instead.")
        else -> onNote("Phone info could not be opened on this phone.")
    }
}

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
        // NETWORK SWITCHER - big Material 3 card. One tap opens
        // the system Phone info screen, where the network mode
        // (2G / 3G / 4G / 5G) can be switched.
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
                        Icon(Icons.Default.SignalCellularAlt, contentDescription = null, modifier = Modifier.size(minDim * 0.061f), tint = onPageColor)
                    }
                    Spacer(modifier = Modifier.width(minDim * 0.033f))
                    Text("Network Switcher", fontWeight = FontWeight.SemiBold, fontSize = (minDim * 0.05f).value.sp, color = onPageColor, modifier = Modifier.weight(1f))
                }
                Text(
                    "Open Phone info to switch between 2G, 3G, 4G and 5G.",
                    fontSize = (minDim * 0.039f).value.sp,
                    color = colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = { openNetworkSwitcher(context, onNote) },
                    modifier = Modifier.fillMaxWidth().height(minDim * 0.144f),
                    shape = RoundedCornerShape(minDim * 0.033f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = onPageColor,
                        contentColor = cardBg
                    )
                ) {
                    Text(
                        "Open Phone Info",
                        fontSize = (minDim * 0.044f).value.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
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

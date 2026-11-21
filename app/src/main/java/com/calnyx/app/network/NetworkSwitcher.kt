// FILE: app/src/main/java/com/calnyx/app/network/NetworkSwitcher.kt
// NETWORK SWITCHER - SEPARATE FILE (2026-11-21).
//  Split out of NetworkScreen.kt: the Phone info opener
//  (openPhoneInfo) + the Network Switcher card now live here,
//  so NetworkScreen.kt only holds the page + the other cards.
//  Nothing changed in the logic - moved as-is.
//  One tap opens the system Phone info (Testing) screen to
//  switch 2G / 3G / 4G / 5G. See openPhoneInfo below for the
//  fallback chain.
// THEME VARIABLES + % SIZES: same as NetworkScreen - colours
//  from the theme, every size a % of minDim, no fixed dp/sp
//  except 1.dp borders.
package com.calnyx.app.network

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

// NETWORK SWITCHER - big Material 3 card. One tap opens
// the system Phone info screen, where the network mode
// (2G / 3G / 4G / 5G) can be switched.
@Composable
fun NetworkSwitcherCard(
    cardBg: Color,
    cardBorder: Color,
    onPageColor: Color,
    thumbBg: Color,
    minDim: Dp,
    onNote: (String?) -> Unit
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
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
}

// FILE: app/src/main/java/com/calnyx/app/ui/screens/BlackHoleSetupScreen.kt

// MODERN VERSION - replace your old BlackHoleSetupScreen.kt with this file.
// Logic is UNCHANGED (first-time setup asks no old secret, changing
// a set secret requires the old secret, Save stores it via
// BlackHolePrefs and calls onSaved). Only the design changed:
//  - Black/white only (off-white / off-black), dark-mode support,
//    no theme pink on the button or the text fields.
//  - English only. No Banglish strings, no long descriptions,
//    no warning paragraph — title and field labels only.
//  - Reached only by long-pressing "=" in the Normal calculator
//    while no secret is set. There is no menu trace of it.
// B/W AUDIT (2026-11-06): app-theme fix only — this screen now
// takes darkTheme: Boolean = isSystemInDarkTheme() and uses it
// instead of reading the system theme, so the app's own theme
// toggle controls it. No chromatic colour was found anywhere
// (no primary / error): button, fields and borders were already
// onPageColor / cardBorderColor only. No other change.
// NO DRAWER SWIPE on this screen: it is a hidden screen —
// NavGraph deliberately hides the drawer and top bar on the
// BlackHoleSetup route, and the blackhole must stay hidden with no menu
// trace. Do NOT add any onOpenDrawer param or swipe detector here.
package com.calnyx.app.blackhole

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.calnyx.app.blackhole.BlackHolePrefs

@Composable
fun BlackHoleSetupScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    darkTheme: Boolean = isSystemInDarkTheme()
) {
    val context = LocalContext.current
    val alreadySet = BlackHolePrefs.isSecretSet(context)

    var oldSecret by remember { mutableStateOf("") }
    var newSecret by remember { mutableStateOf("") }
    var confirmSecret by remember { mutableStateOf("") }

    // All colours come from the theme (Color.kt) - see the note at
    // the top of this file. darkTheme is kept only because NavGraph
    // passes it; colours no longer depend on it.
    val colorScheme = MaterialTheme.colorScheme
    val pageBg = colorScheme.background
    val onPageColor = colorScheme.onBackground
    val cardBorderColor = colorScheme.outline
    val thumbBg = colorScheme.surfaceVariant

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = onPageColor,
        unfocusedBorderColor = cardBorderColor,
        focusedTextColor = onPageColor,
        unfocusedTextColor = onPageColor,
        cursorColor = onPageColor,
        focusedLabelColor = onPageColor.copy(alpha = 0.6f),
        unfocusedLabelColor = onPageColor.copy(alpha = 0.6f)
    )

    Column(
        modifier = Modifier.fillMaxSize()
            .background(pageBg)
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = onPageColor
                )
            }
            Text(
                text = "Setup",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
                color = onPageColor,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier.size(40.dp)
                    .clip(CircleShape)
                    .background(thumbBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint = onPageColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        if (alreadySet) {
            OutlinedTextField(
                value = oldSecret,
                onValueChange = { oldSecret = it.filter { c -> c.isDigit() } },
                label = { Text("Old secret") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(16.dp),
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        OutlinedTextField(
            value = newSecret,
            onValueChange = { newSecret = it.filter { c -> c.isDigit() } },
            label = { Text("New secret") },
            singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(16.dp),
            colors = fieldColors,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = confirmSecret,
            onValueChange = { confirmSecret = it.filter { c -> c.isDigit() } },
            label = { Text("Confirm secret") },
            singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(16.dp),
            colors = fieldColors,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                val fresh = newSecret.trim()
                when {
                    fresh.isBlank() -> {
                        Toast.makeText(context, "Secret cannot be empty", Toast.LENGTH_SHORT).show()
                    }
                    fresh != confirmSecret.trim() -> {
                        Toast.makeText(context, "Secrets do not match", Toast.LENGTH_SHORT).show()
                    }
                    alreadySet && oldSecret.trim() != BlackHolePrefs.getSecret(context) -> {
                        Toast.makeText(context, "Old secret is incorrect", Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        BlackHolePrefs.setSecret(context, fresh)
                        Toast.makeText(context, "Secret saved", Toast.LENGTH_SHORT).show()
                        onSaved()
                    }
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = onPageColor,
                contentColor = pageBg
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text("Save")
        }
    }
}



// NETWORK + THEME VARIABLES VERSION (2026-11-20) - REPLACE NavGraph.kt.
//  1. NEW Screen.Network ("network", "Network", Icons.Default.SignalCellularAlt),
//     in menuScreens after Screen.Moan, with its own composable below.
//     Network is a tool screen (in persistRoutes, NOT in calculatorRoutes),
//     so reopening saves Normal instead, like every other tool screen.
//  2. COLOURS: the hardcoded Nav* palette (NavOffWhite / NavOffBlack /
//     NavCardWhite / NavCardBlack / NavBorder* / NavDrawerEdgeDark /
//     NavThumb*) is gone - pageBg / cardBg / onPageColor / borders now
//     come from the theme (Color.kt) through MaterialTheme.colorScheme.
//     The red drawer badge is CalNyxRed now (same value as NavRed).
//     The update status colours (green / yellow / red) stay as they
//     are - they are the user's explicit exception to the B/W rule
//     (green = up to date, yellow = checking, red = update available).
//     The drawer scrim stays black, as before.
//  3. LauncherSettings is NOT in this file any more (removed 2026-11-14,
//     user decision) - only its old header comment above remains.
// LAUNCHER SETTINGS VERSION (2026-11-12) - REPLACE NavGraph.kt.
// NO-ANIMATION FIX (2026-11-12): NavHost transitions removed - Enter/Exit None.
// The fade+slide transition showed the old screen behind the new one
// and felt slow. Navigation is now instant. Drawer slide/scrim kept
// (that is the drawer itself, not a screen transition).
//  Based on NAVBAR % + SCROLLABLE DRAWER (the full NavGraph pasted 2026-11-12).
//  ONLY Launcher Settings changes, everything else byte-identical:
//  1. NEW Screen.LauncherSettings ("launcher_settings", "Launcher", Icons.Default.Home).
//  2. menuScreens: Screen.LauncherSettings added last, after Screen.Clipboard.
//  3. NEW NavHost entry composable(Screen.LauncherSettings.route) -> LauncherSettingsScreen(onOpenDrawer = { drawerOpen = true }).
//  4. NEW imports: Icons.Default.Home and com.calnyx.app.launcher.LauncherSettingsScreen.
//  Reopen rule unchanged: LauncherSettings is in persistRoutes but NOT in calculatorRoutes,
//  so reopening saves Normal instead, like every other tool screen.
// NAVBAR % + SCROLLABLE DRAWER VERSION (2026-11-07) - REPLACE NavGraph.kt.
// REOPEN RULE FIX (2026-11-08, V-REOPEN), base = dev-contact NavGraph:
//  The saved route only fixed a FRESH start. Minimise + reopen keeps
//  the live back stack, so Spy (or any tool screen) stayed on top and
//  its drawer item stayed active - even while recording. Now the
//  ON_START guard sends ANY non-calculator screen back to the
//  remembered calculator (Normal, or Scientific / CalTape if that
//  was the last calculator). A restore effect covers process death
//  while in front, where the backgrounded flag is false. Spy
//  RECORDING itself is untouched: the foreground service keeps
//  recording, only the visible screen resets to the calculator.
//  Decoy (App Lock) still always lands on Normal, vault privacy
//  pop still runs first, rotation / picker return are unaffected
//  (no backgrounded flag is set for them).
//  Mini/split/floating-e drawer scroll hoto na, karon: (1) drawer width
//  LocalConfiguration (DEVICE width) * 2/3 chilo - window choto holeo
//  drawer device-er moto choura hoto, screen-er baire chole jeto;
//  (2) drawer Column-e kono verticalScroll chilo na, ar Spacer
//  weight(1f) About item-ke niche thele window-er baire pathato.
//  V: drawer width = WINDOW width-er % (normal 66.7%, mini 85%),
//  tools list = weight(1f) + verticalScroll, About = fixed footer
//  (kokhono baire jabe na), header/icon/font/padding/gap - sob %.
//  Top bar-o % -e. Logic/routes/swipe/privacy - kichu bodlayni.
// ONBOARDING VERSION (2026-11-06) - REPLACE NavGraph.kt.
//  1. FIRST RUN: if OnboardingPrefs says the introduction was
//     never finished, CalNyxNavGraph shows OnboardingScreen
//     INSTEAD of the calculator (early return, before any
//     other remember). Finishing it (or Skip) saves done=true
//     and the calculator appears. Clearing app data shows it
//     again. Nothing else in the graph changed.
//  2. ABOUT: the About modal now has a Permissions section
//     (same OnboardingPermissions list as the introduction),
//     with each permission's state and a Grant button, so a
//     permission missed or skipped in the introduction can
//     be granted later from here.
// ABOUT-CRASH FIX (2026-11-06) - REPLACE NavGraph.kt.
//  About tapped -> app closed. Likely cause: the dialog
//  loaded the launcher icon with painterResource(
//  R.mipmap.ic_launcher). On this project the launcher
//  icon is an ADAPTIVE icon (mipmap-anydpi-v26 XML),
//  and painterResource cannot load an adaptive-icon
//  drawable - it throws and the app dies. Fixed by
//  loading the icon through PackageManager instead
//  (getApplicationIcon -> draw into a Bitmap ->
//  asImageBitmap), which works for ANY drawable,
//  adaptive included, with a Calculate-icon fallback
//  if even that fails. No other change.
// SPY + HARDWARE/SOUND + ABOUT VERSION (2026-11-06) - REPLACE NavGraph.kt.
//  1. RENAMED (drawer + page titles only; routes, function
//     names and behaviour unchanged): Hardcore -> Hardware,
//     Moan -> Sound, Blue Film -> Spy.
//  2. DRAWER SWIPE: HardcoreScreen / MoanScreen /
//     ThirdEyeScreen now get onOpenDrawer, so a leftward
//     swipe anywhere on Hardware, Sound or Spy opens the
//     drawer (universal drawer rule).
//  3. ABOUT / UPDATE (swapped 2026-11-07): the header
//     top-right icon is now an Info (i) button - tap it and
//     the About modal opens; the bottom footer item is now
//     Update - tap it and the UpdateDialog opens. Function
//     unchanged, only the places swapped. The About modal
//     itself: app icon + CalNyx + version on top,
//     a Check for updates button (opens the existing
//     UpdateDialog), a description of the app, and below
//     it a Developer Contact row - tap it to open
//     masudibnbelat.vercel.app in the browser.

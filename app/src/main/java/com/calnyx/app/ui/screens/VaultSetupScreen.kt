// FILE: app/src/main/java/com/calnyx/app/ui/screens/VaultSetupScreen.kt
// MODERN VERSION - replace your old VaultSetupScreen.kt with this file.
// Logic is UNCHANGED (first-time setup asks no old secret, changing
// a set secret requires the old secret, Save stores it via
// VaultPrefs and calls onSaved). Only the design changed:
//  - Black/white only (off-white / off-black), dark-mode support,
//    no theme pink on the button or the text fields.
//  - English only. No Banglish strings, no long descriptions,
//    no warning paragraph — title and field labels only.
//  - Reached only by long-pressing "=" in the Normal calculator
//    while no secret is set. There is no menu trace of it.
package com.calnyx.app.ui.screens

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
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.calnyx.app.vault.VaultPrefs

@Composable
fun VaultSetupScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val alreadySet = VaultPrefs.isSecretSet(context)

    var oldSecret by remember { mutableStateOf("") }
    var newSecret by remember { mutableStateOf("") }
    var confirmSecret by remember { mutableStateOf("") }

    // MODERN palette: only off-white / off-black / grey.
    val isDark = isSystemInDarkTheme()
    val pageBg = if (isDark) Color(0xFF0E0E0E) else Color(0xFFF6F4EF)
    val onPageColor = if (isDark) Color(0xFFF6F4EF) else Color(0xFF0E0E0E)
    val cardBorderColor = if (isDark) Color(0xFF2B2B2B) else Color(0xFFE9E6DF)
    val thumbBg = if (isDark) Color(0xFF262626) else Color(0xFFEFEDE8)

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
                onValueChange = { oldSecret = it },
                label = { Text("Old secret") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        OutlinedTextField(
            value = newSecret,
            onValueChange = { newSecret = it },
            label = { Text("New secret") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = fieldColors,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = confirmSecret,
            onValueChange = { confirmSecret = it },
            label = { Text("Confirm secret") },
            singleLine = true,
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
                    alreadySet && oldSecret.trim() != VaultPrefs.getSecret(context) -> {
                        Toast.makeText(context, "Old secret is incorrect", Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        VaultPrefs.setSecret(context, fresh)
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

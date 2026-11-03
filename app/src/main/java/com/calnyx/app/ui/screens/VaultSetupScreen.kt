// FILE: app/src/main/java/com/calnyx/app/ui/screens/VaultSetupScreen.kt
// First-time vault setup. Reached from:
//  1. the menu sheet ("Hidden Space setup"), or
//  2. long-pressing "=" in the Normal calculator while no
//     secret has been set yet.
// If no secret is set yet, NO old secret is asked — that is
// the whole point: a new user must not need to know a
// developer default. If a secret is already set, the old
// secret must be entered first, so a stranger cannot change it.
package com.calnyx.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.calnyx.app.vault.VaultPrefs

@Composable
fun VaultSetupScreen(onBack: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val alreadySet = VaultPrefs.isSecretSet(context)

    var oldSecret by remember { mutableStateOf("") }
    var newSecret by remember { mutableStateOf("") }
    var confirmSecret by remember { mutableStateOf("") }

    Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp)) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                        text = "Hidden Space setup",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f)
                )
                Icon(Icons.Default.Lock, contentDescription = null)
            }
            Spacer(modifier = Modifier.height(20.dp))

            Text(
                    text = if (alreadySet) "Secret bodlao" else "Prothombar secret set koro",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                    text =
                            if (alreadySet) {
                                "Purono secret na janle bodlano jabe na. " +
                                        "Notun secret-ta type kore = chaplei vault khulbe."
                            } else {
                                "Kono purono secret lagbe na. " +
                                        "Tumi ekhane ja set korbe, pore Normal calculator-e " +
                                        "oi hisab-ta type kore = chaplei Hidden Space khulbe. " +
                                        "Jemon secret 69 hole, pore 69 likhe = chapte hobe."
                            },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(20.dp))

            if (alreadySet) {
                OutlinedTextField(
                        value = oldSecret,
                        onValueChange = { oldSecret = it },
                        label = { Text("Purono secret") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            OutlinedTextField(
                    value = newSecret,
                    onValueChange = { newSecret = it },
                    label = { Text("Notun secret hisab") },
                    placeholder = { Text("Jemon: 69, ba 1234, ba 12+34") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                    value = confirmSecret,
                    onValueChange = { confirmSecret = it },
                    label = { Text("Notun secret abar likho") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(20.dp))

            Button(
                    onClick = {
                        val fresh = newSecret.trim()
                        when {
                            fresh.isBlank() -> {
                                Toast.makeText(
                                                context,
                                                "Secret khali rakha jabe na",
                                                Toast.LENGTH_SHORT
                                        )
                                        .show()
                            }
                            fresh != confirmSecret.trim() -> {
                                Toast.makeText(context, "Dubar lekha milche na", Toast.LENGTH_SHORT)
                                        .show()
                            }
                            alreadySet && oldSecret.trim() != VaultPrefs.getSecret(context) -> {
                                Toast.makeText(context, "Purono secret thik na", Toast.LENGTH_SHORT)
                                        .show()
                            }
                            else -> {
                                VaultPrefs.setSecret(context, fresh)
                                Toast.makeText(context, "Secret set hoyeche", Toast.LENGTH_SHORT)
                                        .show()
                                onSaved()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
            ) { Text("Save secret") }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                    text =
                            "Sotorko: secret bhule gele vault khola jabe na, " +
                                    "karon er kono recovery nei. Secret-ta kothao likhe rekho.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

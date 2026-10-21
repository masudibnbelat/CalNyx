package com.calnyx.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinancialCalculatorScreen(
    darkTheme: Boolean,
    onToggleTheme: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Financial Calculator") },
                actions = {
                    IconButton(onClick = onToggleTheme) {
                        Icon(
                            if (darkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Toggle Theme"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Financial Calculator",
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Coming soon...\n\nEMI, Interest, Loan, SIP, Compound Interest, NPV, IRR etc.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

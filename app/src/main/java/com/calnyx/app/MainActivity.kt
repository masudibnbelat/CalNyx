// FILE: app/src/main/java/com/calnyx/app/MainActivity.kt
package com.calnyx.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.calnyx.app.navigation.CalNyxNavGraph
import com.calnyx.app.ui.theme.CalNyxTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val systemDarkTheme = isSystemInDarkTheme()
            var darkTheme by remember { mutableStateOf(systemDarkTheme) }

            CalNyxTheme(darkTheme = darkTheme) {
                CalNyxNavGraph(
                    darkTheme = darkTheme,
                    onToggleTheme = { darkTheme = !darkTheme }
                )
            }
        }
    }
}

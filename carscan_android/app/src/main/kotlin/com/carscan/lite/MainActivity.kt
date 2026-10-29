package com.carscan.lite

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.carscan.ui.screens.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.carscan.core.viewmodel.SessionManager.instance.appContext = applicationContext

        // Configure system bars for matching dark theme
        window.statusBarColor = android.graphics.Color.parseColor("#1A202C")
        window.navigationBarColor = android.graphics.Color.parseColor("#12151A")
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        setContent {
            MaterialTheme(
                colorScheme = androidx.compose.material3.darkColorScheme(
                    background = androidx.compose.ui.graphics.Color(0xFF12151A),
                    surface = androidx.compose.ui.graphics.Color(0xFF1C212B)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = androidx.compose.ui.graphics.Color(0xFF12151A)
                ) {
                    var currentRoute by remember { mutableStateOf("home") }

                    when (currentRoute) {
                        "home" -> HomeScreen(onNavigate = { route -> currentRoute = route })
                        "vehicle" -> VehicleScreen(onBack = { currentRoute = "home" })
                        "errors" -> ErrorsScreen(onBack = { currentRoute = "home" })
                        "dashboard" -> DashboardScreen(onBack = { currentRoute = "home" })
                        "table" -> TableScreen(onBack = { currentRoute = "home" })
                        "graph" -> GraphScreen(onBack = { currentRoute = "home" })
                        "log" -> LogScreen(onBack = { currentRoute = "home" })
                        "settings" -> SettingsScreen(onBack = { currentRoute = "home" })
                        else -> HomeScreen(onNavigate = { route -> currentRoute = route })
                    }
                }
            }
        }
    }
}

package com.example.batterieleiste

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.batterieleiste.data.SettingsRepository
import com.example.batterieleiste.ui.*
import com.example.batterieleiste.ui.theme.BatterieleisteTheme

/**
 * MainActivity for Batterieleiste.
 * Hosts the Bento-style dashboard and manages the foreground service lifecycle.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable full edge-to-edge display for a modern look
        enableEdgeToEdge()
        
        val repository = SettingsRepository(applicationContext)
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel(repository) as T
            }
        }

        setContent {
            BatterieleisteTheme {
                val viewModel: SettingsViewModel = viewModel(factory = factory)
                val isServiceEnabled by viewModel.isServiceEnabled.collectAsStateWithLifecycle()
                val navController = rememberNavController()

                // Reactively manage the BatteryForegroundService based on settings and permissions
                LaunchedEffect(isServiceEnabled) {
                    val serviceIntent = Intent(this@MainActivity, BatteryForegroundService::class.java)
                    if (isServiceEnabled && Settings.canDrawOverlays(this@MainActivity)) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(serviceIntent)
                        } else {
                            startService(serviceIntent)
                        }
                    } else {
                        // Ensure service is stopped if disabled or permission is missing
                        stopService(serviceIntent)
                    }
                }

                NavHost(navController = navController, startDestination = "dashboard") {
                    composable("dashboard") {
                        DashboardScreen(viewModel = viewModel, navController = navController)
                    }
                    composable("activation") {
                        ActivationScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                    }
                    composable("app_theme") {
                        AppThemeScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                    }
                    composable("bar_style") {
                        BarStyleScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}

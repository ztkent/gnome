package com.ztkent.gnome

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ztkent.gnome.model.DeviceListModel
import com.ztkent.gnome.ui.theme.SunlightMeterTheme

class SharedState {
    var refreshing by mutableStateOf(false)
}

class SunlightActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SunlightMeterTheme {
                val navController = rememberNavController() // Create NavController here
                val viewModel = DeviceListModel(this@SunlightActivity)
                NavHost(navController = navController, startDestination = "home") {
                    composable("home") {
                        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                            HomeScreen(
                                modifier = Modifier.padding(innerPadding),
                                viewModel = viewModel,
                                navController = navController
                            )
                        }
                    }
                    composable("graph/{deviceAddr}") { backStackEntry ->
                        val deviceAddr = backStackEntry.arguments?.getString("deviceAddr")
                        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                            GraphScreen(
                                modifier = Modifier.padding(innerPadding),
                                viewModel = viewModel,
                                navController = navController,
                                deviceAddr = deviceAddr ?: ""
                            )
                        }
                    }
                    composable("settings") {
                        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                            SettingsScreen(
                                modifier = Modifier.padding(innerPadding),
                                viewModel = viewModel,
                                navController = navController
                            )
                        }
                    }
                    composable("devicesettings/{deviceAddr}") { backStackEntry ->
                        val deviceAddr = backStackEntry.arguments?.getString("deviceAddr")
                        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                            DeviceSettingsScreen(
                                modifier = Modifier.padding(innerPadding),
                                viewModel = viewModel,
                                navController = navController,
                                deviceAddr = deviceAddr ?: ""
                            )
                        }
                    }
                }
            }
        }
    }
}
package com.ztkent.sunlightmeter

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.ztkent.sunlightmeter.data.AvailableDevices
import com.ztkent.sunlightmeter.ui.theme.SunlightMeterTheme

class SunlightActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val availableDevices = AvailableDevices()
        val devices = availableDevices.getAvailableDevices(this)
        for (device in devices) {
            Log.d("SunlightActivity", "Available device: $device")
        }

        setContent {
            SunlightMeterTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    HomeScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    // Content of your home screen
    Text("This is the home screen!", modifier = modifier)
}
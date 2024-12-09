package com.ztkent.sunlightmeter

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ztkent.sunlightmeter.data.AvailableDevices
import com.ztkent.sunlightmeter.ui.theme.SunlightMeterTheme
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.test.core.app.ApplicationProvider

class SunlightActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SunlightMeterTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    HomeScreen(modifier = Modifier.padding(innerPadding), viewModel = DeviceListModel(this))
                }
            }
        }
    }
}

@Composable
fun HomeScreen(modifier: Modifier = Modifier, viewModel: DeviceListModel) {
    val devicesState by viewModel.devices.collectAsState()
    val devices = devicesState
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF87CEEB), Color(0xFF00BFFF))
                )
            )
    ) {
        // Content of your home screen
        Text("This is the home screen!", modifier = Modifier
            .align(Alignment.TopCenter)
            .offset(0.dp, 75.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.66f) // Occupy bottom 2/3
                .align(Alignment.BottomCenter)
                .background(Color.White.copy(alpha = 0.2f)) // Semi-transparent white background
                .padding(16.dp)
        ) {
            Column (
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ){
                when (devices) {
                    is DeviceLoadState.Loading -> {
                        CircularProgressIndicator() // Show loading indicator
                    }
                    is DeviceLoadState.Success -> {
                        Column {
                            for (device in devices.data) {
                                Button(onClick = { /* Handle device click */ }) {
                                    Text(device)
                                }
                            }
                        }
                    }
                    is DeviceLoadState.Error -> {
                        Text("Error loading devices: ${devices.exception.message}")
                    }
                }
            }
        }
    }
}

// Device List Model
open class DeviceListModel(sunlightActivity: SunlightActivity) : ViewModel() {
    val _devices = MutableStateFlow<DeviceLoadState>(DeviceLoadState.Loading)
    val devices: StateFlow<DeviceLoadState> = _devices.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val availableDevices = AvailableDevices()
                val loadedDevices = availableDevices.getAvailableDevices(sunlightActivity)
                for (device in loadedDevices) {
                    Log.d("SunlightActivity", "Available device: $device")
                }
                _devices.value = DeviceLoadState.Success(loadedDevices)
            } catch (e: Exception) {
                _devices.value = DeviceLoadState.Error(e)
            }
        }
    }
}
sealed class DeviceLoadState {
    object Loading : DeviceLoadState()
    data class Success(val data: List<String>) : DeviceLoadState()
    data class Error(val exception: Exception) : DeviceLoadState()
}


// Tests
@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    SunlightMeterTheme {
        HomeScreen(viewModel = DeviceListModelPreview()) // Provide mock ViewModel
    }
}

class DeviceListModelPreview : DeviceListModel(ApplicationProvider.getApplicationContext()) {
    init {
        _devices.value = DeviceLoadState.Success(listOf("Device 1", "Device 2", "Device 3"))
    }
}
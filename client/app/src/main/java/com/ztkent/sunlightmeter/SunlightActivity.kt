package com.ztkent.sunlightmeter

import com.ztkent.sunlightmeter.data.Device
import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.ztkent.sunlightmeter.ui.theme.BG1
import com.ztkent.sunlightmeter.ui.theme.BG2
import com.ztkent.sunlightmeter.ui.theme.DIVIDER_COLOR
import com.ztkent.sunlightmeter.ui.theme.NotificationBarColor
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
    var refresh by remember { mutableStateOf(false) }
    val devicesState by viewModel.devices.collectAsState()
    val devices = devicesState
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        BG1, BG2
                    )
                )
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFD9D9D9))
                .height(55.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.header), // Replace with your image
                contentDescription = "Header image",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.95f) // Occupy bottom 2/3
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            Column (
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ){
                when (devices) {
                    is DeviceLoadState.Loading -> {
                        CircularProgressIndicator(
                            color = Color.Black,
                            modifier = Modifier
                                .offset(0.dp, 120.dp)
                        ) // Show loading indicator
                    }
                    is DeviceLoadState.Success -> {
                        Column {
                            for (device in devices.data) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .offset(0.dp, 5.dp)
                                        .padding(8.dp)
                                        .height(265.dp)
                                        .background(
                                            Color.White,
                                            shape = RoundedCornerShape(
                                                8.dp
                                            )
                                        ) // Rounded corners
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.network_wifi),
                                        contentDescription = "Device image",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .offset(8.dp,4.dp)
                                            .align(Alignment.TopStart)
                                    )
                                    Text(
                                        text = device.addr,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily(
                                            Font(R.font.roboto)
                                        ),
                                        color = Color.Black,
                                        modifier = Modifier
                                            .offset(32.dp,4.dp)
                                            .align(Alignment.TopStart)
                                    )
                                    Text(
                                        text = "Connected",
                                        fontSize = 16.sp,
                                        fontFamily = FontFamily(
                                            Font(R.font.roboto)
                                        ),
                                        color = Color.Black,
                                        modifier = Modifier
                                            .offset(8.dp,24.dp)
                                            .align(Alignment.TopStart)
                                    )
                                    IconButton(onClick = { /* TODO */ },
                                        modifier = Modifier
                                            .offset((-16).dp, 20.dp)
                                            .align(Alignment.TopEnd)
                                            .size(20.dp)
                                    ) {
                                        Icon(
                                            Icons.Outlined.Settings,
                                            contentDescription = "Notifications",
                                            tint = Color.Gray
                                        )
                                    }
                                    HorizontalDivider(
                                        color = DIVIDER_COLOR,
                                        thickness = 1.dp,
                                        modifier = Modifier
                                            .offset(0.dp,60.dp)
                                    )
                                    Text(
                                        text = "1200 lm",
                                        fontSize = 36.sp,
                                        fontFamily = FontFamily(
                                            Font(R.font.roboto)
                                        ),
                                        color = Color.Black,
                                        modifier = Modifier
                                            .padding(12.dp)
                                            .align(Alignment.Center)
                                            .offset(0.dp, (-20).dp)
                                    )
                                }
                            }
                        }
                    }
                    is DeviceLoadState.Error -> {
                        Text("${devices.exception.message}")
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(NotificationBarColor)
                .padding(8.dp)
                .height(48.dp) // Increased height for better icon visibility
                .align(Alignment.BottomCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceAround // Distribute icons evenly
            ) {
                IconButton(onClick = {
                    // Reload the current Composable
                    refresh = !refresh
                }) {
                    Icon(Icons.Filled.Refresh,
                        contentDescription = "Refresh", tint = Color.Black)
                }
                IconButton(onClick = { /* TODO */ }) {
                    Icon(Icons.Filled.Add,
                        contentDescription = "Add Device", tint = Color.Black)
                }
                IconButton(onClick = { /* TODO */ }) {
                    Icon(Icons.Outlined.Settings,
                        contentDescription = "Settings", tint = Color.Black)
                }
            }
            LaunchedEffect(refresh) {
                if (refresh) {
                    viewModel.refresh()
                    refresh = false
                }
            }
        }
    }
}

// Device List Model
open class DeviceListModel(sunlightActivity: SunlightActivity) : ViewModel() {
    val _devices = MutableStateFlow<DeviceLoadState>(DeviceLoadState.Loading)
    val devices: StateFlow<DeviceLoadState> = _devices.asStateFlow()
    val slActivity = sunlightActivity

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

    fun refresh() {
        viewModelScope.launch {
            try {
                _devices.value = DeviceLoadState.Loading
                val availableDevices = AvailableDevices()
                val loadedDevices = availableDevices.getAvailableDevices(
                    slActivity)
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
    data object Loading : DeviceLoadState()
    data class Success(val data: List<Device>) : DeviceLoadState()
    data class Error(val exception: Exception) : DeviceLoadState()
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    SunlightMeterTheme {
        HomeScreen(viewModel = DeviceListModelPreview()) // Use the preview model
    }
}

class MockAvailableDevices : AvailableDevices() {
    override suspend fun getAvailableDevices(context: Context): List<Device> {
        return listOf(Device("FakeDevice1"), Device("FakeDevice2"))
    }
}
class DeviceListModelPreview : DeviceListModel(SunlightActivity()) {
    init {
        viewModelScope.launch {
            // Use the fake data for preview
            _devices.value = DeviceLoadState.Success(MockAvailableDevices().getAvailableDevices(SunlightActivity()))
        }
    }
}
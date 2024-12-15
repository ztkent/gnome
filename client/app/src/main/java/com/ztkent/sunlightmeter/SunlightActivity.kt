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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.constraintlayout.widget.ConstraintLayout
import com.ztkent.sunlightmeter.ui.theme.BG1
import com.ztkent.sunlightmeter.ui.theme.BG2
import com.ztkent.sunlightmeter.ui.theme.DIVIDER_COLOR
import com.ztkent.sunlightmeter.ui.theme.NotificationBarColor
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.constraintlayout.compose.ConstraintLayout
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.google.accompanist.swiperefresh.SwipeRefreshIndicator

class SunlightActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SunlightMeterTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    HomeScreen2(modifier = Modifier.padding(innerPadding), viewModel = DeviceListModel(this))
                }
            }
        }
    }
}

@Composable
fun HomeScreen(modifier: Modifier = Modifier, viewModel: DeviceListModel) {
    ConstraintLayout(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(BG1, BG2)
                )
            )
    ) {
        val (header, deviceList, bottomBar) = createRefs()
        Box(
            modifier = Modifier
                .constrainAs(header) {
                    top.linkTo(parent.top)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                }
                .fillMaxWidth()
                .background(Color(0xFFD9D9D9))
                .height(55.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.header),
                contentDescription = "Header image",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        val devicesState by viewModel.devices.collectAsState()
        val devices = devicesState
        LazyColumn(
            modifier = Modifier
                .constrainAs(deviceList) {
                    top.linkTo(header.bottom)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                    bottom.linkTo(bottomBar.top)
                }
                .fillMaxSize(),
            contentPadding = PaddingValues(top = 60.dp)
        ) {
            when (devices) {
                is DeviceLoadState.Loading -> {
                    item {
                        CircularProgressIndicator(
                            color = Color.Black,
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentSize(Alignment.Center)
                                .padding(top = 120.dp) // Adjust padding as needed
                        )
                    }
                }
                is DeviceLoadState.Success -> {
                    if (devices.data.isEmpty()) {
                        // Display some information
                        item {
                            DeviceItem(device = Device("No Devices Found"))
                        }
                    } else {
                        items(
                            items = devices.data,
                            key = { device -> device.addr } // Provide a unique key for each device
                        ) { device ->
                            DeviceItem(device = device)
                        }
                    }
                }
                is DeviceLoadState.Error -> {
                    item {
                        Text(
                            text = "Error loading devices: ${devices.exception.message}",
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentSize(Alignment.Center)
                        )
                    }
                }
            }
        }

        var refresh by remember { mutableStateOf(false) }
        Box(
            modifier = Modifier
                .constrainAs(bottomBar) {
                    bottom.linkTo(parent.bottom)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                }
                .fillMaxWidth()
                .background(NotificationBarColor)
                .padding(8.dp)
                .height(48.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                IconButton(onClick = { refresh = !refresh }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = Color.Black)
                }
                IconButton(onClick = { /* TODO */ }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add Device", tint = Color.Black)
                }
                IconButton(onClick = { /* TODO */ }) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = Color.Black)
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

@Composable
fun HomeScreen2(modifier: Modifier = Modifier, viewModel: DeviceListModel) {
    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(refreshing) {
        if (refreshing) {
            viewModel.refresh()
            refreshing = false
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
            ConstraintLayout(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(BG1, BG2)
                        )
                    )
            ) {
                val (header, deviceList, bottomBar) = createRefs()
                Box(
                    modifier = Modifier
                        .constrainAs(header) {
                            top.linkTo(parent.top)
                            start.linkTo(parent.start)
                            end.linkTo(parent.end)
                        }
                        .fillMaxWidth()
                        .background(Color(0xFFD9D9D9))
                        .height(55.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.header),
                        contentDescription = "Header image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                SwipeRefresh(
                    state = rememberSwipeRefreshState(isRefreshing = refreshing),
                    onRefresh = { refreshing = true },
                    indicator = { state, trigger ->
                        SwipeRefreshIndicator(
                            state = state,
                            refreshTriggerDistance = trigger,
                            contentColor = Color.Black // or any other color you prefer
                        )
                    }
                ) {
                val devicesState by viewModel.devices.collectAsState()
                val devices = devicesState
                LazyColumn(
                    modifier = Modifier
                        .constrainAs(deviceList) {
                            top.linkTo(header.bottom)
                            start.linkTo(parent.start)
                            end.linkTo(parent.end)
                            bottom.linkTo(bottomBar.top)
                        }
                        .fillMaxSize(),
                    contentPadding = PaddingValues(top = 60.dp)
                ) {
                    when (devices) {
                        is DeviceLoadState.Loading -> {
                            item {
                                CircularProgressIndicator(
                                    color = Color.Black,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .wrapContentSize(Alignment.Center)
                                        .padding(top = 120.dp) // Adjust padding as needed
                                )
                            }
                        }
                        is DeviceLoadState.Success -> {
                            if (devices.data.isEmpty()) {
                                // Display some information
                                item {
                                    DeviceItem(device = Device("No Devices Found"))
                                }
                            } else {
                                items(
                                    items = devices.data,
                                    key = { device -> device.addr } // Provide a unique key for each device
                                ) { device ->
                                    DeviceItem(device = device)
                                }
                            }
                        }
                        is DeviceLoadState.Error -> {
                            item {
                                Text(
                                    text = "Error loading devices: ${devices.exception.message}",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .wrapContentSize(Alignment.Center)
                                )
                            }
                        }
                    }
                    }
                }

                var refresh by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .constrainAs(bottomBar) {
                            bottom.linkTo(parent.bottom)
                            start.linkTo(parent.start)
                            end.linkTo(parent.end)
                        }
                        .fillMaxWidth()
                        .background(NotificationBarColor)
                        .padding(8.dp)
                        .height(48.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        IconButton(onClick = { refresh = !refresh }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = Color.Black)
                        }
                        IconButton(onClick = { /* TODO */ }) {
                            Icon(Icons.Filled.Add, contentDescription = "Add Device", tint = Color.Black)
                        }
                        IconButton(onClick = { /* TODO */ }) {
                            Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = Color.Black)
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
}

@Composable
fun DeviceItem(device: Device) {
    ConstraintLayout(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .height(265.dp)
            .background(
                Color.White,
                shape = RoundedCornerShape(8.dp)
            )
    ) {
        val (image, address, status, settingsIcon, divider, brightness) = createRefs()
        Image(
            painter = painterResource(id = R.drawable.network_wifi),
            contentDescription = "Device image",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .constrainAs(image) {
                    top.linkTo(parent.top, margin = 4.dp)
                    start.linkTo(parent.start, margin = 8.dp)
                }
        )
        Text(
            text = device.addr,
            fontSize = 12.sp,
            fontFamily = FontFamily(Font(R.font.roboto)),
            color = Color.Black,
            modifier = Modifier
                .constrainAs(address) {
                    top.linkTo(parent.top, margin = 4.dp)
                    start.linkTo(image.end, margin = 8.dp)
                }
        )
        Text(
            text = "Connected",
            fontSize = 16.sp,
            fontFamily = FontFamily(Font(R.font.roboto)),
            color = Color.Black,
            modifier = Modifier
                .constrainAs(status) {
                    top.linkTo(image.bottom, margin = 8.dp)
                    start.linkTo(parent.start, margin = 8.dp)
                }
        )

        IconButton(
            onClick = { /* TODO */ },
            modifier = Modifier
                .constrainAs(settingsIcon) {
                    top.linkTo(parent.top, margin = 20.dp)
                    end.linkTo(parent.end, margin = 16.dp)
                }
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
                .constrainAs(divider) {
                    top.linkTo(status.bottom, margin = 16.dp)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                }
        )

        Text(
            text = "1200 lm",
            fontSize = 36.sp,
            fontFamily = FontFamily(Font(R.font.roboto)),
            color = Color.Black,
            modifier = Modifier
                .constrainAs(brightness) {
                    centerVerticallyTo(parent)
                    centerHorizontallyTo(parent)
                }
                .padding(12.dp)
        )
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
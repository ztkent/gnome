package com.ztkent.gnome

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.launch
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.PullRefreshState
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.ztkent.gnome.data.AvailableDevices
import com.ztkent.gnome.data.Conditions
import com.ztkent.gnome.data.Device
import com.ztkent.gnome.data.Errors
import com.ztkent.gnome.data.SignalStrength
import com.ztkent.gnome.data.Status
import com.ztkent.gnome.ui.theme.BG1
import com.ztkent.gnome.ui.theme.BG2
import com.ztkent.gnome.ui.theme.DIVIDER_COLOR
import com.ztkent.gnome.ui.theme.LuxColorDarkOvercast
import com.ztkent.gnome.ui.theme.LuxColorDarkTwilight
import com.ztkent.gnome.ui.theme.LuxColorDirectSunlight
import com.ztkent.gnome.ui.theme.LuxColorFullDaylight
import com.ztkent.gnome.ui.theme.LuxColorFullMoon
import com.ztkent.gnome.ui.theme.LuxColorLivingRoom
import com.ztkent.gnome.ui.theme.LuxColorMoonlessOvercast
import com.ztkent.gnome.ui.theme.LuxColorOfficeHallway
import com.ztkent.gnome.ui.theme.LuxColorOfficeLighting
import com.ztkent.gnome.ui.theme.LuxColorOvercastDay
import com.ztkent.gnome.ui.theme.LuxColorSunriseSunset
import com.ztkent.gnome.ui.theme.LuxColorTrainStation
import com.ztkent.gnome.ui.theme.NotificationBarColor
import com.ztkent.gnome.ui.theme.SELECTED_TAB_COLOR
import com.ztkent.gnome.ui.theme.SunlightMeterTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
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

class SharedState {
    var refreshing by mutableStateOf(false)
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun HomeScreen(modifier: Modifier = Modifier, viewModel: DeviceListModel) {
    // Setup pull refresh
    val sharedState = remember { SharedState() } // Create shared state
    val pullRefreshState = rememberPullRefreshState(sharedState.refreshing, { sharedState.refreshing = true })
    LaunchedEffect(sharedState.refreshing) { // Observe shared state
        if (sharedState.refreshing) {
            viewModel.refresh()
            sharedState.refreshing = false
        }
    }

    // Setup for landscape config
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(modifier = modifier.fillMaxSize()) {
        if (isLandscape) {
            LandscapeMode(pullRefreshState, viewModel, sharedState)
        } else {
            PortraitMode(pullRefreshState, viewModel, sharedState)
        }
    }
}

@Composable
@OptIn(ExperimentalMaterialApi::class)
private fun LandscapeMode(
    pullRefreshState: PullRefreshState,
    viewModel: DeviceListModel,
    sharedState: SharedState // Receive shared state
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {    // Landscape layout
        Row(
            modifier = Modifier
                .fillMaxSize()
        ) {
            ConstraintLayout(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(BG1, BG2)
                        )
                    )
            ) {
                val (deviceList, bottomBar, refreshIndicator) = createRefs()
                val devicesState by viewModel.devices.collectAsState()
                val devices = devicesState
                LazyRow(
                    modifier = Modifier
                        .constrainAs(deviceList) {
                            top.linkTo(parent.top)
                            start.linkTo(parent.start)
                            bottom.linkTo(bottomBar.top)
                        },
                    contentPadding = PaddingValues(top = 45.dp, bottom = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    state = rememberLazyListState()
                ) {
                    when (devices) {
                        is DeviceLoadState.Loading -> {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillParentMaxSize() // Fill the entire LazyRow space
                                        .wrapContentSize(Alignment.Center) // Center content within the Box
                                ) {
                                    CircularProgressIndicator(
                                        color = Color.Black
                                    )
                                }
                            }
                        }

                        is DeviceLoadState.Success -> {
                            if (devices.data.isEmpty()) {
                                item {
                                    EmptyDeviceItem(
                                        device = Device(""),
                                        modifier = Modifier
                                            .fillParentMaxWidth(0.5f)
                                            .padding(8.dp)
                                    )
                                }
                            } else {
                                items(devices.data) { device -> // Iterate over individual devices
                                    DeviceItem(
                                        device = device,
                                        viewModel = viewModel,
                                        modifier = Modifier
                                            .fillParentMaxWidth(0.5f)
                                            .padding(8.dp)
                                    )
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
                        IconButton({}) {
                            Icon(
                                Icons.Filled.Home,
                                contentDescription = "Home",
                                tint = SELECTED_TAB_COLOR
                            )
                        }
                        IconButton(onClick = { /* TODO */ }) {
                            Icon(
                                painter = painterResource(id = R.drawable.solar),
                                contentDescription = "Shop", tint = Color.Black
                            )
                        }
                        IconButton(onClick = { /* TODO */ }) {
                            Icon(
                                Icons.Outlined.Settings,
                                contentDescription = "Settings",
                                tint = Color.Black
                            )
                        }
                        IconButton(onClick = { sharedState.refreshing = !sharedState.refreshing }) {
                            Icon(
                                Icons.Outlined.Refresh,
                                contentDescription = "Refresh",
                                tint = Color.Black
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterialApi::class)
private fun PortraitMode(
    pullRefreshState: PullRefreshState,
    viewModel: DeviceListModel,
    sharedState: SharedState // Receive shared state
) {
        // Portrait Layout
        ConstraintLayout(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(BG1, BG2)
                    )
                )
                .pullRefresh(pullRefreshState)
        ) {
            val (header, deviceList, bottomBar, refreshIndicator) = createRefs()
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
                contentPadding = PaddingValues(top = 65.dp ,bottom = 65.dp)
            ) {
                when (devices) {
                    is DeviceLoadState.Loading -> {
                        item {
                            CircularProgressIndicator(
                                color = Color.Black,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentSize(Alignment.Center)
                                    .padding(top = 120.dp)
                            )
                        }
                    }

                    is DeviceLoadState.Success -> {
                        if (devices.data.isEmpty()) {
                            // Display some information
                            item {
                                EmptyDeviceItem(device = Device(""),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(8.dp)
                                )
                            }
                        } else {
                            items(
                                items = devices.data.sortedBy { it.addr },
                                key = { device -> device.addr }
                            ) { device ->
                                DeviceItem(
                                    device = device,
                                    viewModel = viewModel,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp)
                                )
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
                    IconButton({}) {
                        Icon(
                            Icons.Filled.Home,
                            contentDescription = "Home",
                            tint = SELECTED_TAB_COLOR
                        )
                    }
                    IconButton(onClick = { /* TODO */ }) {
                        Icon(
                            painter = painterResource(id = R.drawable.solar),
                            contentDescription = "Shop", tint = Color.Black
                        )
                    }
                    IconButton(onClick = { /* TODO */ }) {
                        Icon(
                            Icons.Outlined.Settings,
                            contentDescription = "Settings",
                            tint = Color.Black
                        )
                    }
                }
            }

            PullRefreshIndicator(
                sharedState.refreshing,
                pullRefreshState,
                Modifier.constrainAs(refreshIndicator) {
                    top.linkTo(header.bottom)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                },
                contentColor = Color.Black
            )
        }
}

@Composable
fun DeviceItem(device: Device, viewModel : DeviceListModel, modifier: Modifier = Modifier) {
    val coroutineScope = rememberCoroutineScope()
    var backgroundDeviceColor = when (device.conditions.lux) {
        in 0..2 -> LuxColorMoonlessOvercast
        in 3..20 -> LuxColorFullMoon // Special case for 3.4 lux
        in 20..49 -> LuxColorDarkTwilight
        in 50..50 -> LuxColorLivingRoom
        in 51..79 -> LuxColorOfficeHallway
        in 80..99 -> LuxColorDarkOvercast
        in 100..149 -> LuxColorTrainStation
        in 150..319 -> LuxColorOfficeLighting
        in 320..399 -> LuxColorSunriseSunset
        in 400..999 -> LuxColorOvercastDay
        in 1000..9999 -> LuxColorFullDaylight
        in 10000..15000 -> LuxColorDirectSunlight
        else -> Color.White // Default to white for unknown lux levels
    }
    if (!device.status.enabled) {
        backgroundDeviceColor = Color.White
    }
    ConstraintLayout(
        modifier = modifier
            .height(265.dp)
            .background(
                backgroundDeviceColor,
                shape = RoundedCornerShape(8.dp)
            )
    ) {
        val (image, address, status, settingsIcon, divider, brightness, divider2, refreshIcon, graphIcon, downloadIcon) = createRefs()
        val wifiId = when {
            device.signalStrength.strength > 80 -> R.drawable.network_wifi
            device.signalStrength.strength > 60 -> R.drawable.network_wifi_3
            device.signalStrength.strength > 40 -> R.drawable.network_wifi_2
            device.signalStrength.strength > 20 -> R.drawable.network_wifi_1
            else -> R.drawable.network_wifi_off
        }
        Image(
            painter = painterResource(id = wifiId),
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
            fontSize = 14.sp,
            fontFamily = FontFamily(Font(R.font.roboto)),
            color = Color.Black,
            modifier = Modifier
                .constrainAs(address) {
                    top.linkTo(parent.top, margin = 4.dp)
                    start.linkTo(image.end, margin = 8.dp)
                }
        )
        Text(
            text = if (device.status.connected) "Connected" else "Sensor Disconnected",
            fontSize = 16.sp,
            fontFamily = FontFamily(Font(R.font.roboto)),
            color = Color.Black,
            modifier = Modifier
                .constrainAs(status) {
                    top.linkTo(image.bottom, margin = 4.dp)
                    start.linkTo(parent.start, margin = 8.dp)
                }
        )

        IconButton(
            onClick = {
                coroutineScope.launch(Dispatchers.IO) {
                   device.flipStatus().fold(
                        onSuccess = {
                            viewModel.updateDevice(device,250)
                        },
                        onFailure = { exception ->
                            Log.e("DeviceItem", "Error adjusting device power", exception)
                        }
                    )
                }
            },
            modifier = Modifier
                .constrainAs(settingsIcon) {
                    top.linkTo(parent.top, margin = 8.dp)
                    end.linkTo(parent.end, margin = 4.dp)
                }
        ) {
            Icon(
                painterResource(id = R.drawable.power_28),
                contentDescription = "Notifications",
                tint = Color.Black
            )
        }

        HorizontalDivider(
            color = DIVIDER_COLOR,
            thickness = 1.dp,
            modifier = Modifier
                .constrainAs(divider) {
                    top.linkTo(status.bottom, margin = 8.dp)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                }
        )

        if (device.status.enabled) {
            Text(
                text = "" + device.conditions.lux + " lm",
                fontSize = 36.sp,
                fontFamily = FontFamily(Font(R.font.roboto)),
                color = Color.Black,
                modifier = Modifier
                    .constrainAs(brightness) {
                        centerVerticallyTo(parent)
                        centerHorizontallyTo(parent)
                        bottom.linkTo(parent.bottom, margin = -10.dp)
                    }
                    .padding(12.dp)
            )
        } else {
            Text(
                text = "Disabled",
                fontSize = 36.sp,
                fontFamily = FontFamily(Font(R.font.robotolight)),
                color = Color.Black,
                modifier = Modifier
                    .constrainAs(brightness) {
                        centerVerticallyTo(parent)
                        centerHorizontallyTo(parent)
                    }
                    .padding(12.dp)
            )
        }
        HorizontalDivider(
            color = DIVIDER_COLOR,
            thickness = 1.dp,
            modifier = Modifier
                .constrainAs(divider2) {
                    bottom.linkTo(parent.bottom, margin = 60.dp)
                }
        )
        IconButton(
            onClick = {
                coroutineScope.launch(Dispatchers.IO) {
                    device.refreshDevice().fold(
                        onSuccess = {
                            viewModel.updateDevice(device)
                        },
                        onFailure = { exception ->
                            Log.e("DeviceItem", "Error refreshing device", exception)
                        }
                    )
                }
            },
            modifier = Modifier
                .constrainAs(refreshIcon) {
                    bottom.linkTo(parent.bottom, margin = 10.dp)
                    start.linkTo(parent.start, margin = 40.dp)
                }
                .size(45.dp)
        ) {
            Icon(
                painterResource(id = R.drawable.refresh_32),
                contentDescription = "Refresh Device",
                tint = Color.Black
            )
        }
        IconButton(
            onClick = { /* TODO */ },
            modifier = Modifier
                .constrainAs(graphIcon) {
                    bottom.linkTo(parent.bottom, margin = 10.dp)
                    start.linkTo(parent.start, margin = 0.dp)
                    end.linkTo(parent.end, margin = 0.dp)
                }
                .size(45.dp)
        ) {
            Icon(
                painterResource(id = R.drawable.auto_graph_32),
                contentDescription = "Notifications",
                tint = Color.Black
            )
        }
        IconButton(
            onClick = {
                coroutineScope.launch(Dispatchers.IO) {
                    device.getDataExport(viewModel.slActivity).fold(
                        onSuccess = {
                            Log.i("DeviceItem", "Data export successful")
                        },
                        onFailure = { exception ->
                            Log.e("DeviceItem", "Error getting device export", exception)
                        }
                    )
                }
            },
            modifier = Modifier
                .constrainAs(downloadIcon) {
                    bottom.linkTo(parent.bottom, margin = 10.dp)
                    end.linkTo(parent.end, margin = 40.dp)
                }
                .size(45.dp)
        ) {
            Icon(
                painterResource(id = R.drawable.download_32),
                contentDescription = "Notifications",
                tint = Color.Black
            )
        }
    }
}

@Composable
fun EmptyDeviceItem(device: Device, modifier: Modifier = Modifier) {
    ConstraintLayout(
        modifier = modifier
            .height(265.dp)
            .background(
                Color.White,
                shape = RoundedCornerShape(8.dp)
            )
    ) {
        val (image, address, status, infoIcon, divider, brightness) = createRefs()
        Image(
            painter = painterResource(id = R.drawable.network_wifi_off),
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
        IconButton(
            onClick = { /* TODO */ },
            modifier = Modifier
                .constrainAs(infoIcon) {
                    top.linkTo(parent.top, margin = 12.dp)
                    end.linkTo(parent.end, margin = 4.dp)
                }
        ) {
            Icon(
                painterResource(id = R.drawable.info_28),
                contentDescription = "No Devices",
                tint = Color.Gray
            )
        }
        Text(
            text = "No Devices Found",
            fontSize = 16.sp,
            fontFamily = FontFamily(Font(R.font.roboto)),
            color = Color.Black,
            modifier = Modifier
                .constrainAs(status) {
                    top.linkTo(image.bottom, margin = 8.dp)
                    start.linkTo(parent.start, margin = 8.dp)
                }
        )

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
            text = "N/A",
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
            loadDevices()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            loadDevices()
        }
    }

    fun updateDevice(updatedDevice: Device, delay: Long = 500) {
        if (devices.value is DeviceLoadState.Success) {
            val currentDevices = (_devices.value as DeviceLoadState.Success).data
            val updatedDevices = currentDevices.map {
                if (it.addr == updatedDevice.addr) updatedDevice else it
            }
            _devices.value = DeviceLoadState.Loading // Optional: Show loading state briefly
            Thread.sleep(delay) // Max request time
            _devices.value = DeviceLoadState.Success(updatedDevices)
        }
    }

    private suspend fun loadDevices() {
        try {
            _devices.value = DeviceLoadState.Loading
            val availableDevices = AvailableDevices()
            val loadedDevices = availableDevices.getAvailableDevices(slActivity)
            for (device in loadedDevices) {
                Log.d("SunlightActivity", "Available device: $device")
            }
            _devices.value = DeviceLoadState.Success(loadedDevices)
        } catch (e: Exception) {
            _devices.value = DeviceLoadState.Error(e)
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
        val numDevices = (1..10).random() // Generate a random number between 1 and 10
        return (1..numDevices).map {
            Device(
                addr = "192.168.1.$it",
                serviceName = "Gnome", // or "Sunlight Meter"
                outboundIp = "192.168.1.$it",
                macAddresses = listOf("00:11:22:33:44:$it"),
                signalStrength = SignalStrength(signalInt = -50 + it * 10, strength = (15..100).random()),
                conditions = Conditions(jobID = "job$it", lux = (0..15000).random()),
                status = Status(connected = true, enabled = it % 3 != 0),
                errors = Errors(signalStrength = if (it % 4 == 0) "Signal error" else "No errors")
            )
        }
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
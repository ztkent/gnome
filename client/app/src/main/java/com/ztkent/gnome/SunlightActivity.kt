package com.ztkent.gnome

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.lifecycle.viewModelScope
import com.ztkent.gnome.data.AvailableDevices
import com.ztkent.gnome.data.Device
import com.ztkent.gnome.ui.theme.BG1
import com.ztkent.gnome.ui.theme.BG2
import com.ztkent.gnome.ui.theme.DIVIDER_COLOR
import com.ztkent.gnome.ui.theme.NotificationBarColor
import com.ztkent.gnome.ui.theme.SELECTED_TAB_COLOR
import com.ztkent.gnome.ui.theme.SunlightMeterTheme
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
                                    DeviceItem(
                                        device = Device("No Devices Found"),
                                        modifier = Modifier
                                            .fillParentMaxWidth(0.5f)
                                            .padding(8.dp)
                                    )
                                }
                            } else {
                                items(devices.data) { device -> // Iterate over individual devices
                                    DeviceItem(
                                        device = device,
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
                                DeviceItem(device = Device("No Devices Found"),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(8.dp)
                                )
                            }
                        } else {
                            items(
                                items = devices.data,
                                key = { device -> device.addr } // this should be unique
                            ) { device ->
                                DeviceItem(device = device,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp)
                                )                            }
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
fun DeviceItem(device: Device, modifier: Modifier = Modifier) {
    ConstraintLayout(
        modifier = modifier
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
        val numDevices = (1..10).random() // Generate a random number between 1 and 10
        return (1..numDevices).map { Device("FakeDevice$it") } // Create a list of fake devices
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
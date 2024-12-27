package com.ztkent.gnome

import android.content.ActivityNotFoundException
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.util.Log
import android.widget.Toast
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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.PullRefreshState
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.ztkent.gnome.data.Device
import com.ztkent.gnome.model.DeviceListModel
import com.ztkent.gnome.model.DeviceLoadState
import com.ztkent.gnome.ui.theme.BG1
import com.ztkent.gnome.ui.theme.BG2
import com.ztkent.gnome.ui.theme.DIVIDER_COLOR
import com.ztkent.gnome.ui.theme.LuxColorDarkOvercastGradient
import com.ztkent.gnome.ui.theme.LuxColorDarkTwilightGradient
import com.ztkent.gnome.ui.theme.LuxColorDirectSunlightGradient
import com.ztkent.gnome.ui.theme.LuxColorFullDaylightGradient
import com.ztkent.gnome.ui.theme.LuxColorFullMoonGradient
import com.ztkent.gnome.ui.theme.LuxColorLivingRoomGradient
import com.ztkent.gnome.ui.theme.LuxColorMoonlessOvercastGradient
import com.ztkent.gnome.ui.theme.LuxColorOfficeHallwayGradient
import com.ztkent.gnome.ui.theme.LuxColorOfficeLightingGradient
import com.ztkent.gnome.ui.theme.LuxColorOvercastDayGradient
import com.ztkent.gnome.ui.theme.LuxColorSunriseSunsetGradient
import com.ztkent.gnome.ui.theme.LuxColorTrainStationGradient
import com.ztkent.gnome.ui.theme.LuxDisabledGradient
import com.ztkent.gnome.ui.theme.LuxUnknownGradient
import com.ztkent.gnome.ui.theme.NotificationBarColor
import com.ztkent.gnome.ui.theme.SELECTED_TAB_COLOR
import com.ztkent.gnome.ui.theme.SunlightMeterTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun HomeScreen(modifier: Modifier = Modifier, viewModel: DeviceListModel, navController: NavHostController) {
    // Setup pull refresh
    val sharedState = remember { SharedState() } // Create shared state
    val pullRefreshState = rememberPullRefreshState(sharedState.refreshing, { sharedState.refreshing = true })
    LaunchedEffect(sharedState.refreshing) { // Observe shared state
        if (sharedState.refreshing) {
            viewModel.refresh()
            sharedState.refreshing = false
        }
    }

    viewModel.slActivity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(modifier = modifier.fillMaxSize()) {
        if (isLandscape) {
            LandscapeMode(pullRefreshState, viewModel, sharedState, navController)
        } else {
            PortraitMode(pullRefreshState, viewModel, sharedState, navController)
        }
    }
}

@Composable
@OptIn(ExperimentalMaterialApi::class)
private fun LandscapeMode(
    pullRefreshState: PullRefreshState,
    viewModel: DeviceListModel,
    sharedState: SharedState,
    navController: NavHostController
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
                                        navController = navController,
                                        sharedState = sharedState,
                                        modifier = Modifier
                                            .fillParentMaxWidth(0.5f)
                                            .padding(8.dp)
                                    )
                                }
                            }
                        }

                        is DeviceLoadState.Error -> {
                            var message = devices.exception.message
                            if (message != null && message.contains("Device is not connected to WIFI")){
                                message = "Connect to WIFI to monitor your Gnomes"
                            }
                            item {
                                ErrorItem(
                                    device = Device(""),
                                    msg = message!!,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp)
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
                        val context = LocalContext.current
                        val uriHandler = LocalUriHandler.current
                        IconButton(onClick = {
                            try {
                                uriHandler.openUri("https://www.ztkent.com")
                            } catch (e: ActivityNotFoundException) {
                                Toast.makeText(context, "No browser app found", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(
                                painter = painterResource(id = R.drawable.store_24),
                                contentDescription = "Shop", tint = Color.Black
                            )
                        }
                        IconButton(onClick = { navController.navigate("settings") }) {
                            Icon(
                                Icons.Filled.Settings,
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
    sharedState: SharedState,
    navController: NavHostController
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
                .height(55.dp)
                .zIndex(2f) // Always on top
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
                        // TODO: This should be where the bluetooth/wifi setup process starts
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
                                navController = navController,
                                sharedState = sharedState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp)
                            )
                        }
                    }
                }

                is DeviceLoadState.Error -> {
                    var message = devices.exception.message
                    if (message != null && message.contains("Device is not connected to WIFI")){
                        message = "Connect to WIFI to monitor your Gnomes"
                    }
                    item {
                        ErrorItem(
                            device = Device(""),
                            msg = message!!,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
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

                val context = LocalContext.current
                val uriHandler = LocalUriHandler.current
                IconButton(onClick = {
                    try {
                        uriHandler.openUri("https://www.ztkent.com")
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(context, "No browser app found", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Icon(
                        painter = painterResource(id = R.drawable.store_24),
                        contentDescription = "Shop", tint = Color.Black
                    )
                }
                IconButton(onClick = { navController.navigate("settings") }) {
                    Icon(
                        Icons.Filled.Settings,
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
fun DeviceItem(device: Device, viewModel : DeviceListModel, navController: NavHostController, sharedState: SharedState, modifier: Modifier = Modifier) {
    val coroutineScope = rememberCoroutineScope()
    var showDownloadDialog by remember { mutableStateOf(false) }
    var backgroundDeviceColor = when (device.conditions.lux) {
        in 0..2 -> LuxColorMoonlessOvercastGradient
        in 3..20 -> LuxColorFullMoonGradient // Special case for 3.4 lux
        in 20..49 -> LuxColorDarkTwilightGradient
        in 50..50 -> LuxColorLivingRoomGradient
        in 51..79 -> LuxColorOfficeHallwayGradient
        in 80..99 -> LuxColorDarkOvercastGradient
        in 100..149 -> LuxColorTrainStationGradient
        in 150..319 -> LuxColorOfficeLightingGradient
        in 320..399 -> LuxColorSunriseSunsetGradient
        in 400..999 -> LuxColorOvercastDayGradient
        in 1000..9999 -> LuxColorFullDaylightGradient
        in 10000..30000 -> LuxColorDirectSunlightGradient
        else -> LuxUnknownGradient // Default to white for unknown lux levels
    }
    if (!device.status.enabled) {
        backgroundDeviceColor = LuxDisabledGradient
    }

    ConstraintLayout(
        modifier = modifier
            .height(265.dp)
            .background(
                backgroundDeviceColor,
                shape = RoundedCornerShape(8.dp)
            )
    ) {
        val (image, address, status, settingsIcon, divider, brightness, divider2, bottomRow) = createRefs()
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
            text = if (device.status.connected) "Connected" else "Disconnected",
            fontSize = 14.sp,
            fontFamily = FontFamily(Font(R.font.roboto)),
            color = Color.Black,
            modifier = Modifier
                .constrainAs(status) {
                    top.linkTo(parent.top, margin = 3.dp)
                    start.linkTo(image.end, margin = 8.dp)
                }
        )
        Text(
            text = device.device_prefs_name.ifEmpty { device.addr },
            fontSize = 18.sp,
            fontFamily = FontFamily(Font(R.font.roboto)),
            color = Color.Black,
            modifier = Modifier
                .constrainAs(address) {
                    top.linkTo(image.bottom, margin = 6.dp)
                    start.linkTo(parent.start, margin = 8.dp)
                }
        )

        IconButton(
            onClick = { navController.navigate("devicesettings/${device.addr}") },
            modifier = Modifier
                .constrainAs(settingsIcon) {
                    top.linkTo(parent.top, margin = 6.dp)
                    end.linkTo(parent.end, margin = 4.dp)
                }
        ) {
            Icon(
                painterResource(id = R.drawable.settings_24),
                contentDescription = "Device Settings",
                tint = Color.Black
            )
        }

        HorizontalDivider(
            color = DIVIDER_COLOR,
            thickness = 1.dp,
            modifier = Modifier
                .constrainAs(divider) {
                    top.linkTo(address.bottom, margin = 8.dp)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                }
        )

        if (device.status.sensor_connected && device.status.enabled) {
            Text(
                text = "" + device.conditions.lux + " lx",
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
        } else if (device.status.sensor_connected && !device.status.enabled) {
            Text(
                text = "Disabled",
                fontSize = 32.sp,
                fontFamily = FontFamily(Font(R.font.robotolight)),
                color = Color.Black,
                modifier = Modifier
                    .constrainAs(brightness) {
                        centerVerticallyTo(parent)
                        centerHorizontallyTo(parent)
                    }
                    .padding(12.dp)
            )
        } else {
            Text(
                text = "Sensor Disconnected",
                fontSize = 32.sp,
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

        Row(
            modifier = Modifier
                .constrainAs(bottomRow) {
                    bottom.linkTo(parent.bottom, margin = 10.dp)
                },
            horizontalArrangement = Arrangement.SpaceEvenly
        ){
            IconButton(
                onClick = {
                    sharedState.refreshing = !sharedState.refreshing
                    coroutineScope.launch(Dispatchers.IO) {
                        device.flipStatus().fold(
                            onSuccess = {
                                viewModel.updateDevice(device,250)
                            },
                            onFailure = { exception ->
                                Log.e("DeviceItem", "Error adjusting device power", exception)
                                withContext(Dispatchers.Main) { // Switch to main thread
                                    Toast.makeText(viewModel.slActivity, "Error adjusting device power", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                },
                modifier = Modifier.weight(1f),
                ) {
                Icon(
                    painterResource(id = R.drawable.power_28),
                    contentDescription = "Power",
                    tint = Color.Black
                )
            }
            IconButton(
                onClick = {
                    coroutineScope.launch(Dispatchers.IO) {
                        device.refreshDevice().fold(
                            onSuccess = {
                                viewModel.updateDevice(device)
                            },
                            onFailure = { exception ->
                                Log.e("DeviceItem", "Error refreshing device", exception)
                                withContext(Dispatchers.Main) { // Switch to main thread
                                    Toast.makeText(viewModel.slActivity, "Error refreshing device", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    painterResource(id = R.drawable.refresh_32),
                    contentDescription = "Refresh Device",
                    tint = Color.Black
                )
            }
            IconButton(
                onClick = { navController.navigate("graph/${device.addr}") },
                modifier = Modifier.weight(1f),
                enabled = device.status.connected
            ) {
                Icon(
                    painterResource(id = R.drawable.auto_graph_32),
                    contentDescription = "Notifications",
                    tint = if (device.status.connected) Color.Black else Color.Gray
                )
            }
            IconButton(
                onClick = { showDownloadDialog = true },
                modifier = Modifier.weight(1f),
                enabled = device.status.connected
            ) {
                Icon(
                    painterResource(id = R.drawable.download_32),
                    contentDescription = "Notifications",
                    tint = if (device.status.connected) Color.Black else Color.Gray
                )
            }
        }

        if (showDownloadDialog) {
            AlertDialog(
                onDismissRequest = { showDownloadDialog = false },
                title = { Text("Export Sensor Data") },
                text = { Text("Select the desired export method.") },
                confirmButton = {
                    Row {
                        Button(
                            onClick = {
                                showDownloadDialog = false
                                coroutineScope.launch(Dispatchers.IO) {
                                    device.getDataExport(viewModel.slActivity, "csv").fold(
                                        onSuccess = {
                                            Log.i("DeviceItem", "Data export successful")
                                        },
                                        onFailure = { exception ->
                                            Log.e(
                                                "DeviceItem",
                                                "Error getting device export",
                                                exception
                                            )
                                            withContext(Dispatchers.Main) { // Switch to main thread
                                                Toast.makeText(viewModel.slActivity, "Error getting csv export", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    )
                                }
                            },
                            colors = ButtonColors(
                                NotificationBarColor,
                                contentColor = Color.Black,
                                disabledContainerColor = Color.Gray,
                                disabledContentColor = Color.Black
                            )
                        ) {
                            Text("CSV")
                        }
                        Button(
                            onClick = {
                                showDownloadDialog = false
                                coroutineScope.launch(Dispatchers.IO) {
                                    device.getDataExport(viewModel.slActivity, "sqlite").fold(
                                        onSuccess = {
                                            Log.i("DeviceItem", "Data export successful")
                                        },
                                        onFailure = { exception ->
                                            Log.e(
                                                "DeviceItem",
                                                "Error getting device export",
                                                exception
                                            )
                                            withContext(Dispatchers.Main) { // Switch to main thread
                                                Toast.makeText(viewModel.slActivity, "Error getting sqlite export", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    )
                                }
                            },
                            colors = ButtonColors(
                                NotificationBarColor,
                                contentColor = Color.Black,
                                disabledContainerColor = Color.Gray,
                                disabledContentColor = Color.Black
                            ),
                            modifier = Modifier
                                .padding(start = 8.dp)
                        ) {
                            Text("SQLite DB")

                        }
                    }
                },
                dismissButton = {
                    Button(
                        onClick = {
                            showDownloadDialog = false
                        },
                        colors = ButtonColors(
                            NotificationBarColor,
                            contentColor = Color.Black,
                            disabledContainerColor = Color.Gray,
                            disabledContentColor = Color.Black
                        )
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun ErrorItem(device: Device, msg: String, modifier: Modifier = Modifier) {
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
            text = msg,
            fontSize = 18.sp,
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

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    SunlightMeterTheme {
        val navController = rememberNavController()
        HomeScreen(viewModel = DeviceListModelPreview(), navController = navController)
    }
}
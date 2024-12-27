package com.ztkent.gnome

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults.buttonColors
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuDefaults.outlinedTextFieldColors
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.navigation.NavHostController
import com.ztkent.gnome.data.Device
import com.ztkent.gnome.model.DeviceListModel
import com.ztkent.gnome.ui.theme.BG1
import com.ztkent.gnome.ui.theme.BG2
import com.ztkent.gnome.ui.theme.NotificationBarColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSettingsScreen(
    modifier: Modifier = Modifier,
    navController: NavHostController,
    viewModel: DeviceListModel,
    deviceAddr: String
) {
    var deviceName by remember { mutableStateOf("") }
    var device by remember { mutableStateOf<Device?>(null) }
    LaunchedEffect(Unit) {
        device = viewModel.getDeviceByAddr(deviceAddr)
        device?.let { deviceName = it.device_prefs_name.ifBlank { it.addr } }
    }

    Box(
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
        ) {
            ConstraintLayout(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .weight(1f)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(BG1, BG2)
                        )
                    )
            ) {
                val (header, settingsList, bottomBar) = createRefs()
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

                LazyColumn(
                    modifier = Modifier
                        .constrainAs(settingsList) {
                            top.linkTo(header.bottom)
                            start.linkTo(parent.start)
                            end.linkTo(parent.end)
                            bottom.linkTo(bottomBar.top)
                        }
                        .fillMaxSize(),
                    contentPadding = PaddingValues(top = 65.dp, bottom = 65.dp)
                ) {
                    item {
                        if (device != null) {
                            OutlinedTextField(
                                value = deviceName,
                                onValueChange = { deviceName = it },
                                label = { Text("Device Name") },
                                placeholder = { Text(deviceName) },
                                trailingIcon = {
                                    IconButton(
                                        onClick = { deviceName = "" },
                                        modifier = Modifier
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Clear,
                                            contentDescription = "Clear",
                                            tint = Color.Black
                                        )
                                    }
                                },
                                colors = outlinedTextFieldColors(
                                    cursorColor = Color.Black,
                                    focusedBorderColor = Color.Black,
                                    unfocusedBorderColor = Color.Black,
                                    focusedLabelColor = Color.Black,
                                    unfocusedLabelColor = Color.Black,
                                    focusedTrailingIconColor = Color.Black,
                                    unfocusedTrailingIconColor = Color.Black,
                                    focusedLeadingIconColor = Color.Black,
                                    unfocusedLeadingIconColor = Color.Black,
                                    focusedTextColor = Color.Black,
                                    unfocusedTextColor = Color.Black,
                                    focusedPrefixColor = Color.Black,
                                    unfocusedPrefixColor = Color.Black,
                                    focusedSuffixColor = Color.Black,
                                    unfocusedSuffixColor = Color.Black,
                                    errorBorderColor = Color.Red,
                                    errorCursorColor = Color.Red,
                                    errorLabelColor = Color.Red,
                                    errorLeadingIconColor = Color.Red,
                                    errorTrailingIconColor = Color.Red,
                                    errorTextColor = Color.Red,
                                    errorPrefixColor = Color.Red,
                                    errorSuffixColor = Color.Red
                                ),
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxWidth()
                            )
                        }
                    }
                    if (device?.device_prefs_saved == true) {
                        item {
                            Button(
                                onClick = {
                                    device?.let { viewModel.removeRememberedDevice(device!!) }
                                    viewModel.refresh()
                                    navController.popBackStack()
                                },
                                colors = buttonColors(
                                    containerColor = NotificationBarColor
                                ),
                                modifier = Modifier
                                    .padding(4.dp,8.dp, 4.dp, 0.dp)
                                    .fillMaxWidth()
                            ) {
                                Text(
                                    "Remove",
                                    color = Color.Black
                                )
                            }
                        }
                    }
                    item {
                        Button(
                            onClick = {
                                device?.let { viewModel.addRememberedDevice(it, deviceName) }
                                viewModel.refresh()
                                navController.popBackStack()
                            },
                            colors = buttonColors(
                                containerColor = NotificationBarColor
                            ),
                            modifier = Modifier
                                .padding(4.dp)
                                .fillMaxWidth()
                        ) {
                            Text(
                                "Save",
                                color = Color.Black
                            )
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
                        IconButton(
                            onClick = { navController.popBackStack() },
                            modifier = Modifier
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.Black
                            )
                        }
                    }
                }
            }
        }
    }
}
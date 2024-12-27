package com.ztkent.gnome

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.navigation.NavHostController
import com.ztkent.gnome.model.DeviceListModel
import com.ztkent.gnome.ui.theme.BG1
import com.ztkent.gnome.ui.theme.BG2
import com.ztkent.gnome.ui.theme.NotificationBarColor
import com.ztkent.gnome.ui.theme.SELECTED_TAB_COLOR

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    navController: NavHostController,
    viewModel: DeviceListModel
) {
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
                    .fillMaxWidth() // Changed to fillMaxWidth()
                    .fillMaxHeight() // Changed to fillMaxHeight()
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

                Column(
                    modifier = Modifier
                        .constrainAs(settingsList) {
                            top.linkTo(header.bottom)
                            start.linkTo(parent.start)
                            end.linkTo(parent.end)
                        }
                        .fillMaxSize()
                ) {
                    var selectedUnit by remember { mutableStateOf("Celsius") }
                    var expanded by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .align(Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Temperature Units:", color = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Box {
                            Text(
                                text = selectedUnit,
                                modifier = Modifier.clickable { expanded = !expanded },
                                color = Color.Black
                            )
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Celsius") },
                                    onClick = {
                                        selectedUnit = "Celsius"
                                        expanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Fahrenheit") },
                                    onClick = {
                                        selectedUnit = "Fahrenheit"
                                        expanded = false
                                    }
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
                        IconButton(onClick = {
                            navController.navigate("home")
                        }) {
                            Icon(
                                Icons.Filled.Home,
                                contentDescription = "Home",
                                tint = Color.Black
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
                        IconButton({}) {
                            Icon(
                                Icons.Filled.Settings,
                                contentDescription = "Settings",
                                tint = SELECTED_TAB_COLOR
                            )
                        }
                    }
                }

            }
        }
    }
}
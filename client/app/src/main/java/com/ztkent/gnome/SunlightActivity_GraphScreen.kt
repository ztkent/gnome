package com.ztkent.gnome

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ActivityInfo
import android.icu.util.Calendar
import android.util.Log
import android.view.ViewGroup
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.ztkent.gnome.data.Device
import com.ztkent.gnome.model.DeviceListModel
import com.ztkent.gnome.ui.theme.BG1
import com.ztkent.gnome.ui.theme.BG2
import com.ztkent.gnome.ui.theme.NotificationBarColor
import com.ztkent.gnome.ui.theme.SunlightMeterTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@SuppressLint("SourceLockedOrientationActivity")
@Composable
fun GraphScreen(
    modifier: Modifier = Modifier,
    navController: NavHostController,
    viewModel: DeviceListModel,
    deviceAddr: String
) {
    var isLoading by remember { mutableStateOf(true) }
    val device = viewModel.getDeviceByAddr(deviceAddr)
    var graphData by remember { mutableStateOf<List<Device.GraphData>?>(null) }

    LaunchedEffect(Unit) {
        viewModel.slActivity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        graphData = device?.let { fetchGraphData(viewModel.slActivity, it) }
        if (graphData == null) {
            Log.e("GraphScreen", "Graph data is null")
        } else {
            Log.d("GraphScreen", "Graph data: $graphData")
        }
        isLoading = false
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
                    .fillMaxWidth() // Changed to fillMaxWidth()
                    .fillMaxHeight() // Changed to fillMaxHeight()
                    .weight(1f)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(BG1, BG2)
                        )
                    )
            ) {
                val (header, graphList, bottomBar) = createRefs()
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

                Box(
                    modifier = Modifier
                        .constrainAs(graphList) {
                            top.linkTo(header.bottom)
                            start.linkTo(parent.start)
                            end.linkTo(parent.end)
                            bottom.linkTo(bottomBar.top)
                            width = Dimension.fillToConstraints
                            height = Dimension.fillToConstraints
                        }
                        .fillMaxSize()
                )  {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = Color.Black,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        graphData?.let { data ->
                            Log.d("GraphScreen", "Graph data: $data")
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp)
                                    .zIndex(1f)
                            ) {
                                ConstraintLayout(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight()
                                        .zIndex(1f)
                                        .padding(8.dp)
                                ) {
                                    val (luxBox, inputsBox) = createRefs() // Add refs for titles

                                    // Sunlight Graph 1
                                    ConstraintLayout(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .fillMaxHeight(0.45f) // Reduced height for gap
                                            .zIndex(2f)
                                            .background(
                                                NotificationBarColor,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .padding(8.dp)
                                            .constrainAs(luxBox) {
                                                top.linkTo(parent.top, margin = 4.dp) // Constrain to title1
                                                start.linkTo(parent.start)
                                                end.linkTo(parent.end)
                                            }
                                    ) {
                                        SunlightTimeGraph(viewModel, data, modifier = Modifier.padding(4.dp))
                                    }

                                    // Sunlight Graph 2
                                    ConstraintLayout(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .fillMaxHeight(0.45f) // Reduced height for gap
                                            .zIndex(2f)
                                            .background(
                                                NotificationBarColor,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .padding(8.dp)
                                            .constrainAs(inputsBox) {
                                                top.linkTo(luxBox.bottom, margin = 12.dp) // Constrain to luxBox
                                                start.linkTo(parent.start)
                                                end.linkTo(parent.end)
                                            }
                                    ) {
                                        SunlightTimeGraph(viewModel, data, modifier = Modifier.padding(4.dp))
                                    }
                                }
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
                        IconButton(
                            onClick = { navController.navigate("home") },
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

@Composable
fun SunlightTimeGraph(viewModel: DeviceListModel, graphData: List<Device.GraphData>?, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx ->
            LineChart(ctx).apply {
                setTouchEnabled(true)
                isDragEnabled = true
                setScaleEnabled(true)
                setPinchZoom(true)
                description.isEnabled = false

                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        update = { lineChart ->
            if (graphData != null && graphData.isNotEmpty()) {
                val luxEntries = graphData.map { data ->
                    val timeInMillis = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault())
                        .parse(data.created_at)?.time?.toFloat() ?: 0f
                    Entry(timeInMillis, data.lux)
                }
                val visibleEntries = graphData.map { data ->
                    val timeInMillis = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault())
                        .parse(data.created_at)?.time?.toFloat() ?: 0f
                    Entry(timeInMillis, data.visible)
                }
                val infraredEntries = graphData.map { data ->
                    val timeInMillis = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault())
                        .parse(data.created_at)?.time?.toFloat() ?: 0f
                    Entry(timeInMillis, data.infrared)
                }
                val fullEntries = graphData.map { data ->
                    val timeInMillis = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault())
                        .parse(data.created_at)?.time?.toFloat() ?: 0f
                    Entry(timeInMillis, data.full_spectrum)
                }


                val dataSetLux = LineDataSet(luxEntries, "Lux").apply {
                    color = Color.Black.toArgb()
                    setCircleColor(Color.Black.toArgb())
                    lineWidth = 2f
                    circleRadius = 3f
                    valueTextSize = 0f
                    mode = LineDataSet.Mode.CUBIC_BEZIER
                }
                val dataSetFull = LineDataSet(fullEntries, "Full Spectrum Light").apply {
                    color = Color.Gray.toArgb()
                    setCircleColor(Color.Gray.toArgb())
                    lineWidth = 2f
                    circleRadius = 3f
                    valueTextSize = 0f
                    mode = LineDataSet.Mode.CUBIC_BEZIER
                }
                val dataSetVisible = LineDataSet(visibleEntries, "Visible Light").apply {
                    color = Color.Magenta.toArgb()
                    setCircleColor(Color.Magenta.toArgb())
                    lineWidth = 2f
                    circleRadius = 3f
                    valueTextSize = 0f
                    mode = LineDataSet.Mode.CUBIC_BEZIER
                }
                val dataSetInfrared = LineDataSet(infraredEntries, "Infrared Lux").apply {
                    color = Color.Green.toArgb()
                    setCircleColor(Color.Green.toArgb())
                    lineWidth = 2f
                    circleRadius = 3f
                    valueTextSize = 0f
                    mode = LineDataSet.Mode.CUBIC_BEZIER
                }
                lineChart.data = LineData(dataSetLux, dataSetVisible, dataSetInfrared, dataSetFull)

                // Display timestamps on x-axis
                val xAxis = lineChart.xAxis
                xAxis.valueFormatter = object : ValueFormatter() {
                    private val format = SimpleDateFormat("HH:mm", Locale.ENGLISH)
                    override fun getFormattedValue(value: Float): String {
                        return format.format(Date(value.toLong()))
                    }
                }

                // Handle too many data points
                xAxis.labelRotationAngle = 45f // Rotate labels for better visibility
                xAxis.granularity = 60f * 60 * 1000 // Display labels every hour (adjust as needed)
                // xAxis.setLabelCount(5, true) // Display a limited number of labels

                // Set minimum value for y-axis to 0
                val yAxis = lineChart.axisLeft
                yAxis.axisMinimum = 0f

                // Display y-axis labels only on the left
                val axisRight = lineChart.axisRight
                axisRight.isEnabled = false
                yAxis.isEnabled = true

                lineChart.invalidate()
            }
        },
        modifier = modifier
    )
}


suspend fun fetchGraphData(context: Context, device: Device): List<Device.GraphData>? {
    val calendar = Calendar.getInstance()
    val endDate = calendar.time
    calendar.add(Calendar.DAY_OF_YEAR, -1)
    val startDate = calendar.time
    val graphDataResult = device.getGraphData(context, startDate, endDate)
    if (graphDataResult.isSuccess) {
        return graphDataResult.getOrNull()
    } else {
        // Handle error
        return null
    }
}

@Preview(showBackground = true)
@Composable
fun GraphScreenPreview() {
    SunlightMeterTheme {
        val navController = rememberNavController()
        GraphScreen(navController =navController, viewModel = DeviceListModelPreview(), deviceAddr = "192.168.1.9")
    }
}
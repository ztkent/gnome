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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.CombinedData
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.ztkent.gnome.data.DATA_INTERVAL_MINUTES
import com.ztkent.gnome.data.Device
import com.ztkent.gnome.model.DeviceListModel
import com.ztkent.gnome.ui.theme.BG1
import com.ztkent.gnome.ui.theme.BG2
import com.ztkent.gnome.ui.theme.DLILineColor
import com.ztkent.gnome.ui.theme.FullSpectrumColor
import com.ztkent.gnome.ui.theme.InfraredLightColor
import com.ztkent.gnome.ui.theme.LuxChartColor
import com.ztkent.gnome.ui.theme.NotificationBarColor
import com.ztkent.gnome.ui.theme.PPFDBarColor
import com.ztkent.gnome.ui.theme.GnomeTheme
import com.ztkent.gnome.ui.theme.VisibleLightColor
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
    // TODO: this can probably cause problems when it fails?
    val device = viewModel.getDeviceByAddr(deviceAddr)
    var graphData by remember { mutableStateOf<List<Device.GraphData>?>(null) }
    var showPopup by remember { mutableStateOf<String>("") }

    LaunchedEffect(Unit) {
        viewModel.slActivity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        graphData = device?.let { fetchGraphData(viewModel.slActivity, it) }
        if (graphData == null) {
            // TODO: We need to handle this and display something on the screen
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
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp)
                                    .zIndex(1f)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                ConstraintLayout(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp)
                                ) {
                                    val (ppfdBox, luxBox, componentsBox) = createRefs() // Add refs for titles
                                    ConstraintLayout(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(400.dp)
                                            .background(
                                                NotificationBarColor,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .padding(8.dp)
                                            .constrainAs(ppfdBox) {
                                                top.linkTo(
                                                    parent.top,
                                                    margin = 12.dp
                                                )
                                                start.linkTo(parent.start)
                                                end.linkTo(parent.end)
                                            }
                                    ) {
                                        ConstraintLayout(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(8.dp)
                                        ) {
                                            val (title, infoButton, chart) = createRefs()
                                            Text(
                                                text = "Daily Light Integral (mol/m²/day)",
                                                color = Color.Black,
                                                modifier = Modifier
                                                    .constrainAs(title) {
                                                        top.linkTo(parent.top)
                                                        start.linkTo(parent.start)
                                                        end.linkTo(infoButton.start) // Adjust end constraint
                                                        width = Dimension.fillToConstraints
                                                    }
                                                    .padding(4.dp, 4.dp, 0.dp, 4.dp)
                                                    .wrapContentSize(Alignment.Center)
                                            )
                                            IconButton(
                                                onClick = { showPopup = "DLI"},
                                                modifier = Modifier
                                                    .constrainAs(infoButton) {
                                                        top.linkTo(parent.top)
                                                        end.linkTo(parent.end)
                                                        bottom.linkTo(chart.top)
                                                    }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Info, // Use appropriate info icon
                                                    contentDescription = "Info",
                                                    tint = Color.LightGray,
                                                    modifier = Modifier
                                                        .padding(4.dp, 4.dp)
                                                )
                                            }
                                            SunlightPPFDCombinedChart(
                                                viewModel = viewModel,
                                                graphData = data,
                                                modifier = Modifier
                                                    .constrainAs(chart) {
                                                        top.linkTo(title.bottom)
                                                        start.linkTo(parent.start)
                                                        end.linkTo(parent.end)
                                                    }
                                                    .padding(4.dp, 4.dp, 4.dp, 24.dp)
                                            )
                                        }
                                    }
                                    ConstraintLayout(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(400.dp)
                                            .background(
                                                NotificationBarColor,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .padding(8.dp)
                                            .constrainAs(luxBox) {
                                                top.linkTo(
                                                    ppfdBox.bottom,
                                                    margin = 12.dp
                                                ) // Constrain to title1
                                                start.linkTo(parent.start)
                                                end.linkTo(parent.end)
                                            }
                                    ) {
                                        val (title, infoButton, chart) = createRefs()
                                        Text(
                                            text = "Daily Light Conditions",
                                            color = Color.Black,
                                            modifier = Modifier
                                                .constrainAs(title) {
                                                    top.linkTo(parent.top)
                                                    start.linkTo(parent.start)
                                                    end.linkTo(infoButton.start) // Adjust end constraint
                                                    width = Dimension.fillToConstraints
                                                }
                                                .padding(4.dp, 4.dp, 0.dp, 4.dp)
                                                .wrapContentSize(Alignment.Center)
                                        )
                                        IconButton(
                                            onClick = { showPopup = "Lux" },
                                            modifier = Modifier
                                                .constrainAs(infoButton) {
                                                    top.linkTo(parent.top)
                                                    end.linkTo(parent.end)
                                                    bottom.linkTo(chart.top)
                                                }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Info, // Use appropriate info icon
                                                contentDescription = "Info",
                                                tint = Color.LightGray,
                                                modifier = Modifier
                                                    .padding(4.dp, 4.dp)
                                            )
                                        }
                                        SunlightLuxGraph(
                                            viewModel = viewModel,
                                            graphData = data,
                                            modifier = Modifier
                                                .constrainAs(chart) { // Constrain the chart
                                                    top.linkTo(title.bottom) // Position below the text
                                                    start.linkTo(parent.start)
                                                    end.linkTo(parent.end)
                                                }
                                                .padding(4.dp, 4.dp, 4.dp, 28.dp)

                                        )
                                    }
                                    ConstraintLayout(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(400.dp)
                                            .background(
                                                NotificationBarColor,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .padding(8.dp)
                                            .constrainAs(componentsBox) {
                                                top.linkTo(
                                                    luxBox.bottom,
                                                    margin = 12.dp
                                                )
                                                start.linkTo(parent.start)
                                                end.linkTo(parent.end)
                                            }
                                    ) {
                                        val (title, infoButton, chart) = createRefs()
                                        Text(
                                            text = "Light Components Breakdown",
                                            color = Color.Black,
                                            modifier = Modifier
                                                .constrainAs(title) { // Constrain the text
                                                    top.linkTo(parent.top)
                                                    start.linkTo(parent.start)
                                                    end.linkTo(parent.end)
                                                    width =
                                                        Dimension.fillToConstraints // Make text fill width
                                                }
                                                .padding(0.dp, 4.dp, 4.dp, 4.dp)
                                                .wrapContentSize(Alignment.Center) // Center text horizontally
                                        )
                                        IconButton(
                                            onClick = { showPopup = "Components" },
                                            modifier = Modifier
                                                .constrainAs(infoButton) {
                                                    top.linkTo(parent.top)
                                                    end.linkTo(parent.end)
                                                    bottom.linkTo(chart.top)
                                                }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Info, // Use appropriate info icon
                                                contentDescription = "Info",
                                                tint = Color.LightGray,
                                                modifier = Modifier
                                                    .padding(4.dp, 4.dp)
                                            )
                                        }

                                        SunlightComponentsGraph(
                                            viewModel = viewModel,
                                            graphData = data,
                                            modifier = Modifier
                                                .constrainAs(chart) { // Constrain the chart
                                                    top.linkTo(title.bottom) // Position below the text
                                                    start.linkTo(parent.start)
                                                    end.linkTo(parent.end)
                                                }
                                                .padding(4.dp, 4.dp, 4.dp, 28.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (showPopup != "") {
                        if (showPopup == "DLI") {
                            AlertDialog(
                                onDismissRequest = { showPopup = "" },
                                title = { Text("Daily Light Integral") },
                                text = { Text("The estimated amount of photosynthetically active radiation (PAR) that is delivered to a specific area over a 24-hour period. " +
                                        "\n\nIn agriculture and forestry, PPFD can be preferred as it more accurately reflects that amount of light that can activate photosynthesis (400-700nm). " +
                                        "\n\nIndoor plants that receive more than 100-200 μmol m²/s of PPFD generally achieve optimal growth rates and yields. " +
                                        "\n\nFor outdoor plants, target a PPFD reading of 500-700 μmol m²/s. Some plants may perform best at a value exceeding 800-1000 μmol m²/s.") },
                                confirmButton = {
                                    TextButton(onClick = { showPopup = "" }) {
                                        Text("OK", color = Color.White)
                                    }
                                }
                            )
                        } else if (showPopup == "Lux") {
                            AlertDialog(
                                onDismissRequest = { showPopup = "" },
                                title = { Text("Daily Light Conditions") },
                                text = { Text("Lux is the SI unit of illuminance. " +
                                        "\n\nIlluminance (lx) is a measure of the intensity of light on a surface. " +
                                        "\n\nLuminous flux (lm) as a measure of the total amount of visible light present.") },
                                confirmButton = {
                                    TextButton(onClick = { showPopup = "" }) {
                                        Text("OK", color = Color.White)
                                    }
                                }
                            )
                        } else if (showPopup == "Components") {
                            AlertDialog(
                                onDismissRequest = { showPopup = "" },
                                title = { Text("Light Components") },
                                text = { Text("Full-spectrum light covers the electromagnetic spectrum from infrared to near-ultraviolet." +
                                        "\n\nIt contains most wavelengths that are useful to plant or animal life. " +
                                        "\n\nSunlight can be considered full spectrum. The emission spectrum of a given light source can vary." +
                                        "\n\nThis graph represents the normalized emission spectrum of the current light source.")
                                       },
                                confirmButton = {
                                    TextButton(onClick = { showPopup = "" }) {
                                        Text("OK", color = Color.White)
                                    }
                                }
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

@Composable
fun SunlightLuxGraph(viewModel: DeviceListModel, graphData: List<Device.GraphData>?, modifier: Modifier = Modifier) {
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

                val dataSetLux = LineDataSet(luxEntries, "Lux (lm/m²)").apply {
                    color = LuxChartColor.toArgb()
                    setCircleColor(LuxChartColor.toArgb())
                    lineWidth = 2f
                    circleRadius = 3f
                    valueTextSize = 0f
                    mode = LineDataSet.Mode.CUBIC_BEZIER
                }
                lineChart.data = LineData(dataSetLux)

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
                xAxis.setLabelCount(8, true) // Display a limited number of labels

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

@Composable
fun SunlightComponentsGraph(viewModel: DeviceListModel, graphData: List<Device.GraphData>?, modifier: Modifier = Modifier) {
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

                val dataSetFull = LineDataSet(fullEntries, "Full Spectrum Light").apply {
                    color = FullSpectrumColor.toArgb()
                    setCircleColor(FullSpectrumColor.toArgb())
                    lineWidth = 2f
                    circleRadius = 3f
                    valueTextSize = 0f
                    mode = LineDataSet.Mode.HORIZONTAL_BEZIER
                }
                val dataSetVisible = LineDataSet(visibleEntries, "Visible Light").apply {
                    color = VisibleLightColor.toArgb()
                    setCircleColor(VisibleLightColor.toArgb())
                    lineWidth = 2f
                    circleRadius = 3f
                    valueTextSize = 0f
                    mode = LineDataSet.Mode.HORIZONTAL_BEZIER
                }
                val dataSetInfrared = LineDataSet(infraredEntries, "Infrared Lux").apply {
                    color = InfraredLightColor.toArgb()
                    setCircleColor(InfraredLightColor.toArgb())
                    lineWidth = 2f
                    circleRadius = 3f
                    valueTextSize = 0f
                    mode = LineDataSet.Mode.HORIZONTAL_BEZIER
                }
                lineChart.data = LineData(dataSetVisible, dataSetInfrared, dataSetFull)

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
                 xAxis.setLabelCount(8, true) // Display a limited number of labels

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

@Composable
fun SunlightPPFDCombinedChart(viewModel: DeviceListModel, graphData: List<Device.GraphData>?, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx ->
            CombinedChart(ctx).apply {
                setTouchEnabled(true)
                isDragEnabled = false // Disable dragging for bar chart
                setScaleEnabled(true)
                setPinchZoom(false) // Disable pinch zoom for bar chart
                description.isEnabled = false

                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        update = { combinedChart ->
            if (graphData != null && graphData.isNotEmpty()) {
                val ppfdEntries = calculateCumulativePPFD(graphData)
                val dataSetPPFD = BarDataSet(ppfdEntries, "Photosensitive Flux Density (μmol/m²/s)").apply {
                    color = PPFDBarColor.toArgb()
                    valueTextSize = 0f
                }
                val dataSetLine = LineDataSet(ppfdEntries, "DLI").apply { // Empty label for the line
                    color = DLILineColor.toArgb() // Choose your desired line color
                    lineWidth = 2f
                    setCircleColor(DLILineColor.toArgb())
                    circleRadius = 3f
                    mode = LineDataSet.Mode.HORIZONTAL_BEZIER
                    valueTextSize = 0f // Hide values on the line
                }
                val barData = BarData(dataSetPPFD)
                val lineData = LineData(dataSetLine)

                val combinedData = CombinedData()
                combinedData.setData(barData)
                combinedData.setData(lineData)
                combinedChart.data = combinedData
                val legend = combinedChart.legend
                legend.isEnabled = true // Enable the legend
                if (legend.entries.size > 1) {
                    legend.setCustom(arrayOf(legend.entries[1]))
                }

                // Display timestamps on x-axis
                val xAxis = combinedChart.xAxis
                xAxis.valueFormatter = object : ValueFormatter() {
                    private val format = SimpleDateFormat("HH:mm", Locale.ENGLISH)
                    override fun getFormattedValue(value: Float): String {
                        val calendar = Calendar.getInstance()
                        calendar.set(Calendar.HOUR_OF_DAY, value.toInt()) // Set the hour of the day
                        calendar.set(Calendar.MINUTE, 0) // Set minutes to 0 (optional)
                        calendar.set(Calendar.SECOND, 0) // Set seconds to 0 (optional)
                        return format.format(calendar.time)
                    }
                }

                xAxis.labelRotationAngle = 45f // Rotate labels for better visibility
                xAxis.setLabelCount(8, true) // Display a limited number of labels

                // Set minimum value for y-axis to 0
                val yAxis = combinedChart.axisLeft
                yAxis.axisMinimum = 0f

                // Display y-axis labels only on the left
                val axisRight = combinedChart.axisRight
                axisRight.isEnabled = false
                yAxis.isEnabled = true

                combinedChart.invalidate()
            }
        },
        modifier = modifier
    )
}

fun calculateCumulativePPFD(graphData: List<Device.GraphData>): List<BarEntry> {
    val ppfdByHourAndDay = mutableMapOf<Pair<Int, Int>, Float>() // Store PPFD by hour and day
    for (data in graphData) {
        val timeInMillis = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault())
            .parse(data.created_at)?.time ?: 0
        val calendar = Calendar.getInstance().also { it.timeInMillis = timeInMillis }
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val day = calendar.get(Calendar.DAY_OF_YEAR) // Get the day of the year

        val ppfdForInterval = data.lux * 0.0185f * DATA_INTERVAL_MINUTES
        ppfdByHourAndDay[Pair(day, hour)] = (ppfdByHourAndDay[Pair(day, hour)] ?: 0f) + ppfdForInterval
    }

    // Create BarEntry list from the calculated PPFD values
    return ppfdByHourAndDay.entries.map { (dayHour, ppfd) ->
        val (day, hour) = dayHour
        BarEntry( (day * 24 + hour).toFloat(), ppfd) // Use a combined value for x-axis
    }
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
    GnomeTheme {
        val navController = rememberNavController()
        GraphScreen(navController =navController, viewModel = DeviceListModelPreview(), deviceAddr = "192.168.1.9")
    }
}
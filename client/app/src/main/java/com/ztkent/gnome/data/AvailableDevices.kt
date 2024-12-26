package com.ztkent.gnome.data

import android.app.DownloadManager
import android.content.Context
import android.icu.util.Calendar
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.ztkent.gnome.model.DeviceListModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.minutes

const val DATA_INTERVAL_MINUTES = 30

class Device(addr: String) {
    var addr: String
    var serviceName: String = ""
    var outboundIp: String = ""
    var macAddresses: List<String> = emptyList()
    var signalStrength: SignalStrength = SignalStrength()
    var conditions: Conditions = Conditions()
    var status: Status = Status()
    var errors: Errors = Errors()

    var device_prefs_saved: Boolean = false
    var device_prefs_name: String = ""

    init {
        this.addr = addr
    }

    constructor(
        addr: String,
        serviceName: String,
        outboundIp: String,
        macAddresses: List<String>,
        signalStrength: SignalStrength,
        conditions: Conditions,
        status: Status,
        errors: Errors
    ) : this(addr) {
        this.serviceName = serviceName
        this.outboundIp = outboundIp
        this.macAddresses = macAddresses
        this.signalStrength = signalStrength
        this.conditions = conditions
        this.status = status
        this.errors = errors
    }

    fun refreshDevice(): Result<Boolean> {
        return try {
            val res = checkForDeviceResponse(
                host = "",
                ip = this.addr
            )
            if (res.second) {
                res.first?.let { updatedDevice ->
                    this.addr = updatedDevice.addr
                    this.serviceName = updatedDevice.serviceName
                    this.outboundIp = updatedDevice.outboundIp
                    this.macAddresses = updatedDevice.macAddresses
                    this.signalStrength = updatedDevice.signalStrength
                    this.conditions = updatedDevice.conditions
                    this.status = updatedDevice.status
                    this.errors = updatedDevice.errors
                    Result.success(true)
                } ?: Result.failure(IllegalStateException("Updated device is null"))
            } else {
                Result.success(false) // Indicate that the update was not successful
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun flipStatus(): Result<Device> { // Make flipStatus a suspend function
        return try {
            if (this.status.connected && !this.status.enabled) {
                // Turn it on
                val result = withContext(Dispatchers.IO) {
                    callEndpoint(this@Device.addr, "/api/v1/start")
                }

                if (result.isSuccess) {
                    // Refresh the device status after calling the endpoint
                    withContext(Dispatchers.IO) {
                        refreshDevice()
                    }
                     return Result.success(this)
                } else {
                    return Result.failure(result.exceptionOrNull()!!)
                }
            } else if (this.status.connected && this.status.enabled) {
                // Turn it off
                val result = withContext(Dispatchers.IO) {
                    callEndpoint(this@Device.addr, "/api/v1/stop")
                }
                if (result.isSuccess) {
                    // Refresh the device status after calling the endpoint
                    withContext(Dispatchers.IO) {
                        refreshDevice()
                    }
                    return Result.success(this)
                } else {
                    return Result.failure(result.exceptionOrNull()!!)
                }
            } else {
                // We cant do anything, the sensor is not connected.
                Log.e("flipStatus", "Cannot change the status when sensor is disconnected")
                return Result.failure(IllegalStateException("Sensor is disconnected"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getDataExport(context: Context, method: String): Result<String> {
        return try {
            val result = withContext(Dispatchers.IO) {
                if (method == "sqlite"){
                    downloadEndpoint(context, this@Device.addr, "/api/v1/export")
                } else {
                    downloadEndpoint(context, this@Device.addr, "/api/v1/csv")
                }
            }
            if (result.isSuccess) {
                return Result.success("")
            } else {
                return Result.failure(result.exceptionOrNull()!!)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    data class GraphData(
        val job_id: String,
        val lux: Float,
        val full_spectrum: Float,
        val visible: Float,
        val infrared: Float,
        val created_at: String // Assuming created_at is a string representation of a timestamp
    )
    suspend fun getGraphData(context: Context, start: Date, end: Date): Result<List<GraphData>> {
        return try {
            // Format dates as RFC 3339 strings
            val startDateRFC3339 = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX",
                Locale.getDefault()).format(start) // Use full package name
            val endDateRFC3339 = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX",
                Locale.getDefault()).format(end)
            val result = withContext(Dispatchers.IO) {
                getEndpoint(this@Device.addr, "/api/v1/graph?start=$startDateRFC3339&end=$endDateRFC3339")
            }
            if (result.isSuccess) {
                    val jsonResponse = result.getOrNull()
                    if (jsonResponse == "null") {
                        // return empty data for the range
                        val emptyData = mutableListOf<GraphData>()
                        val calendar = Calendar.getInstance()
                        calendar.time = start
                        while (calendar.time.before(end)) {
                            val createdAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault()).format(calendar.time)
                            emptyData.add(GraphData("", 0f, 0f, 0f, 0f, createdAt))
                            calendar.add(Calendar.MINUTE, 60) // Increment by 1 hr
                        }
                        return Result.success(emptyData)
                    }

                    val graphDataList = mutableListOf<GraphData>()
                    val jsonArray = JSONArray(jsonResponse)
                    for (i in 0 until jsonArray.length()) {
                        val jsonObject = jsonArray.getJSONObject(i)
                        val job_id = jsonObject.getString("job_id")
                        val lux = jsonObject.getDouble("lux").toFloat()
                        val full_spectrum = jsonObject.getDouble("full_spectrum").toFloat()
                        val visible = jsonObject.getDouble("visible").toFloat()
                        val infrared = jsonObject.getDouble("infrared").toFloat()
                        val created_at = jsonObject.getString("created_at")

                        graphDataList.add(
                            GraphData(job_id, lux, full_spectrum, visible, infrared, created_at)
                        )
                    }
                    val downsampledData = downsampleData(graphDataList)
                    Result.success(downsampledData)
                } else {
                Result.failure(result.exceptionOrNull()!!)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun downsampleData(data: List<GraphData>): List<GraphData> {
        val downsampledData = mutableListOf<GraphData>()
        val dataByInterval = data.groupBy {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault()).parse(it.created_at)
                ?.let { date ->
                    val interval = DATA_INTERVAL_MINUTES // 30-min interval
                    val calendar = Calendar.getInstance().apply { time = date }
                    val minutes = calendar.get(Calendar.MINUTE)
                    val intervalStart = minutes / interval * interval // Calculate interval start minute
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault()).format(
                        calendar.apply { set(Calendar.MINUTE, intervalStart) }.time
                    )
                }
        }

        dataByInterval.forEach { (intervalTimestamp, dataPoints) ->
            var avgLux = dataPoints.map { it.lux }.average().toFloat()
            var avgFullSpectrum = dataPoints.map { it.full_spectrum }.average().toFloat()
            var avgVisible = dataPoints.map { it.visible }.average().toFloat()
            var avgInfrared = dataPoints.map { it.infrared }.average().toFloat()
            if (avgLux.isNaN()) {
                avgLux = 0f
            }
            if (avgFullSpectrum.isNaN()) {
                avgFullSpectrum = 0f
            }
            if (avgVisible.isNaN()) {
                avgVisible = 0f
            }
            if (avgInfrared.isNaN()) {
                avgInfrared = 0f
            }
            downsampledData.add(
                GraphData("", avgLux, avgFullSpectrum, avgVisible, avgInfrared, intervalTimestamp!!)
            )
        }
        return downsampledData
    }

    private suspend fun callEndpoint(deviceAddress: String, endpoint: String): Result<Unit> {
        return try {
            val url = URL("http://$deviceAddress$endpoint")
            val connection = withContext(Dispatchers.IO) {
                url.openConnection()
            } as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            val responseCode = connection.responseCode
            if (responseCode in 200..299) { // Success response codes
                Result.success(Unit)
            } else {
                Result.failure(Exception("API call failed with response code: $responseCode"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    private suspend fun getEndpoint(deviceAddress: String, endpoint: String): Result<String> {
        return try {
            val url = URL("http://$deviceAddress$endpoint")
            val connection = withContext(Dispatchers.IO) { url.openConnection() } as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                // Read the response as a string
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = StringBuilder()
                var line: String?
                while (withContext(Dispatchers.IO) {
                        reader.readLine()
                    }.also { line = it } != null) {
                    response.append(line)
                }
                withContext(Dispatchers.IO) {
                    reader.close()
                }
                Result.success(response.toString()) // Return the JSON string
            } else {
                Result.failure(Exception("API call failed with response code: $responseCode"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

// Function to store a Device
fun storeDevice(viewModel: DeviceListModel, device: Device) {
    val gson = Gson()
    val json = gson.toJson(device)
    val editor = viewModel.rememberedDevices.edit()
    if (device.macAddresses.isNotEmpty() && device.macAddresses[0].isNotEmpty()) {
        editor.putString(device.macAddresses[0], json) // Use first MAC address as key
        editor.apply()
    }
}

fun removeDevice(viewModel: DeviceListModel, device: Device) {
    val editor = viewModel.rememberedDevices.edit()
    if (device.macAddresses.isNotEmpty() && device.macAddresses[0].isNotEmpty()) {
        editor.remove(device.macAddresses[0]) // Use first MAC address as key
        editor.apply()
    }
}

// Function to return all remembered devices
fun getAllRememberedDevices(viewModel: DeviceListModel): List<Device> {
    val devices = mutableListOf<Device>()
    val gson = Gson()
    viewModel.rememberedDevices.all.forEach { (key, value) ->
        if (value is String) {
            try {
                val device = gson.fromJson(value, Device::class.java)
                devices.add(device)
            } catch (e: JsonSyntaxException) {
                Log.e("DeviceListModel", "Error parsing device JSON for key $key: ${e.message}")
                viewModel.rememberedDevices.edit().remove(key).apply()
            }
        } else {
            Log.w("DeviceListModel", "Unexpected value type for key $key: ${value?.javaClass}")
        }
    }
    return devices
}
private fun downloadEndpoint(context: Context, deviceAddress: String, endpoint: String): Result<Any> {
    return try {
        var filename = "gnome.csv"
        if (endpoint.contains("export")) {
            filename = "gnome.db"
        }
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse("http://$deviceAddress$endpoint"))
            .setTitle(filename)
            .setDescription("Downloading Sensor Data...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
        val downloadId = downloadManager.enqueue(request)
        Result.success(downloadId)
    } catch (e: Exception) {
        Result.failure(e)
    }
}

data class SignalStrength(
    var signalInt: Int = 0,
    var strength: Int = 0
)

data class Conditions(
    var jobID: String = "",
    var lux: Int = 0,
    var fullSpectrum: Int = 0,
    var visible: Int = 0,
    var infrared: Int = 0,
    var dateRange: String = "",
    var recordedHoursInRange: Int = 0,
    var fullSunlightInRange: Int = 0,
    var lightConditionInRange: String = "",
    var averageLuxInRange: Int = 0
)

data class Status(
    var connected: Boolean = false, // If the sensor is plugged in
    var enabled: Boolean = false // If the sensor is turned on
)

data class Errors(
    var signalStrength: String = ""
)

open class AvailableDevices {
    private val coroutineScope = CoroutineScope(Job() + Dispatchers.IO + SupervisorJob())
    private val semaphore = Semaphore(10) // Limit coroutine concurrency to 10 threads

    // Might be able to safely modify concurrently
    private val deviceList = CopyOnWriteArrayList<Device>()
    private var lastChecked = Clock.System.now()

    open suspend fun getAvailableDevices(context: Context): List<Device> {
        if (deviceList.size > 0 && (Clock.System.now() - lastChecked < 10.minutes)) {
            Log.d("getAvailableDevices", "Cached device list: $deviceList")
            return deviceList
        }
        fetchAvailableDevices(context)
        Log.d("getAvailableDevices", "Device list: $deviceList")
        return deviceList
    }

    private suspend fun fetchAvailableDevices(context: Context) {
        // All available devices will be on our network, with a hostname: sunlight.local,
        // or likely the same octet as us. Check the hostname, then every option on the same local network as us.
        // look for a 200 response code at /id
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkCapabilities =
            connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        val ip = getOwnIpAddress(connectivityManager, networkCapabilities)
            ?: throw Exception("Device is not connected to WIFI")


        Log.d("fetchAvailableDevices", "Scanning for devices...")
        val potentialIps = generatePotentialDeviceIps(ip, 100)
        val a =  coroutineScope.async {
            val deferredResults = potentialIps.map { potentialIp ->
                async {
                    try {
                        val devicePair = checkForDeviceResponse(potentialIp, "")
                        if (devicePair.second) {
                            semaphore.acquire() // Acquire a permit before starting
                            addDevice(device = devicePair.first!!)
                            semaphore.release() // Release the permit after finishing
                        } else {
                            Log.d("fetchAvailableDevices", "No device found at $potentialIp")
                        }
                    } catch (e: Exception) {
                        Log.e("fetchAvailableDevices", "Error checking device at $potentialIp: ${e.message}")
                    }
                }
            }

            deferredResults.awaitAll()
            Log.d("fetchAvailableDevices", "Devices found on this network: $deviceList")
        }
        a.await()
        lastChecked = Clock.System.now()
    }

    private fun addDevice(device: Device) {
        if (!deviceList.contains(device)) {
            deviceList.add(device)
        }
    }

    private fun getOwnIpAddress(
        connectivityManager: ConnectivityManager,
        networkCapabilities: NetworkCapabilities?
    ): String? {
        if (networkCapabilities != null &&
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        ) {
            val linkProperties =
                connectivityManager.getLinkProperties(connectivityManager.activeNetwork)
            return linkProperties?.linkAddresses?.firstOrNull { it.address.hostAddress?.contains(".") == true }?.address?.hostAddress
        }
        return null
    }

    private fun generatePotentialDeviceIps(ownIp: String, limit: Int = 254): List<String> {
        val ipParts = ownIp.split(".").toMutableList()
        val potentialIps = mutableListOf<String>()
        for (i in 1..limit) { // Check all possible values for the last octet
            ipParts[3] = i.toString()
            val currentIp = ipParts.joinToString(".")
            if (currentIp != ownIp) {
                potentialIps.add(currentIp)
            }
        }
        return potentialIps
    }
}

private fun checkForDeviceResponse(ip: String, host: String): Pair<Device?, Boolean> {
    var url = "http://$ip/id"
    if (host.isNotEmpty()) {
        url = "http://$host/id"
    }
    try {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 1000 // Adjust timeout as needed
        val responseCode = connection.responseCode

        if (responseCode == HttpURLConnection.HTTP_OK) {
            val inputStream = connection.inputStream
            val reader = BufferedReader(InputStreamReader(inputStream))
            val response = reader.readLine()
            val jsonObject: JSONObject?
            try {
                jsonObject = JSONObject(response)
            } catch (e: Exception) {
                return Pair<Device?, Boolean>(null, false)
            }

            // Check if its one of our devices
            val serviceName = jsonObject.optString("service_name", "") ?: ""
            if (serviceName == "Gnome" || serviceName == "Sunlight Meter") {
                // Extract other fields from jsonObject and populate the Device object
                val device = Device(host.ifEmpty { ip })
                device.serviceName = serviceName
                device.outboundIp = jsonObject.optString("outbound_ip", "")
                device.macAddresses = jsonObject.optJSONArray("mac_addresses")?.let { jsonArray ->
                    (0 until jsonArray.length()).map { jsonArray.getString(it) }
                } ?: emptyList()

                // Extract signalStrength
                val signalStrengthJson = jsonObject.optJSONObject("signal_strength")
                device.signalStrength = SignalStrength(
                    signalStrengthJson?.optInt("signalInt", 0) ?: 0,
                    signalStrengthJson?.optInt("strength", 0) ?: 0
                )

                // Extract conditions
                val conditionsJson = jsonObject.optJSONObject("conditions")
                device.conditions = Conditions(
                    conditionsJson?.optString("jobID", "") ?: "",
                    conditionsJson?.optInt("lux", 0) ?: 0,
                    conditionsJson?.optInt("fullSpectrum", 0) ?: 0,
                    conditionsJson?.optInt("visible", 0) ?: 0,
                    conditionsJson?.optInt("infrared", 0) ?: 0,
                    conditionsJson?.optString("dateRange", "") ?: "",
                    conditionsJson?.optInt("recordedHoursInRange", 0) ?: 0,
                    conditionsJson?.optInt("fullSunlightInRange", 0) ?: 0,
                    conditionsJson?.optString("lightConditionInRange", "") ?: "",
                    conditionsJson?.optInt("averageLuxInRange", 0) ?: 0
                )

                // Extract status
                val statusJson = jsonObject.optJSONObject("status")
                device.status = Status(
                    statusJson?.optBoolean("connected", false) ?: false,
                    statusJson?.optBoolean("enabled", false) ?: false
                )

                // Extract errors
                val errorsJson = jsonObject.optJSONObject("errors")
                device.errors = Errors(
                    errorsJson?.optString("signal_strength", "") ?: ""
                )
                Log.d("checkForDeviceResponse", "Found Device: $device")
                return Pair<Device?, Boolean>(device, true)
            }
            return Pair<Device?, Boolean>(null, false)
        }
    } catch (e: Exception) {
        // Handle exceptions (e.g., timeout, connection refused)
        Log.e("checkForDeviceResponse", "Error checking device at $ip: ${e.message}")
        return Pair<Device?, Boolean>(null, false)
    }
    return Pair<Device?, Boolean>(null, false)
}

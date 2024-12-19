package com.ztkent.gnome.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.minutes

class Device(addr: String) {
    var addr: String
    var serviceName: String = ""
    var outboundIp: String = ""
    var macAddresses: List<String> = emptyList()
    var signalStrength: SignalStrength = SignalStrength()
    var conditions: Conditions = Conditions()
    var status: Status = Status()
    var errors: Errors = Errors()

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
                return Result.success(this)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getDataExport(): Result<String> {
        return try {
            val result = withContext(Dispatchers.IO) {
                callEndpoint(this@Device.addr, "/api/v1/export")
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

package com.ztkent.sunlightmeter.data

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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.datetime.Clock
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.minutes
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

class AvailableDevices {
    private val coroutineScope = CoroutineScope(Job() + Dispatchers.IO + SupervisorJob())
    private val semaphore = Semaphore(10) // Limit coroutine concurrency to 10 threads

    // Might be able to safely modify concurrently
    private val deviceList = CopyOnWriteArrayList<String>()
    private var lastChecked = Clock.System.now()

    suspend fun getAvailableDevices(context: Context): List<String> {
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
        Log.d("fetchAvailableDevices", "Scanning for devices...")
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkCapabilities =
            connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        val ip = getOwnIpAddress(connectivityManager, networkCapabilities)
        if (ip == null) {
            Log.d("fetchAvailableDevices", "Device IP is NULL, not connected to WIFI")
            return
        }

        Log.d("fetchAvailableDevices", "Device IP is $ip")
        Log.d("fetchAvailableDevices", "Checking for devices on this network...")
        val potentialIps = generatePotentialDeviceIps(ip, 100)
        val a =  coroutineScope.async {
            val deferredHost = async {
                checkForDeviceResponse("", "sunlight.local")
            }
            val deferredResults2 = potentialIps.map { potentialIp ->
                async {
                    try {
                        val foundDevice = checkForDeviceResponse(potentialIp, "")
                        if (foundDevice) {
                            semaphore.acquire() // Acquire a permit before starting
                            addDevice(potentialIp)
                            semaphore.release() // Release the permit after finishing
                        } else {
                            Log.d("fetchAvailableDevices", "No device found at $potentialIp")
                        }
                    } catch (e: Exception) {
                        Log.e("fetchAvailableDevices", "Error checking device at $potentialIp: ${e.message}")
                    }
                }
            }

            // Wait for the responses
            deferredHost.await().let { foundDevice ->
                if (foundDevice) {
                    addDevice("sunlight.local")
                }
            }
            deferredResults2.awaitAll()
            Log.d("fetchAvailableDevices", "Devices found on this network: $deviceList")
        }
        a.await()
        lastChecked = Clock.System.now()
    }


    private fun addDevice(device: String) {
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

    private fun checkForDeviceResponse(ip: String, host: String): Boolean {
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
                    return false
                }
                val serviceName = jsonObject.optString("service_name", "") ?: ""
                return serviceName == "Sunlight Meter"
            }
        } catch (e: Exception) {
            // Handle exceptions (e.g., timeout, connection refused)
            return false
        }
        return false
    }
}
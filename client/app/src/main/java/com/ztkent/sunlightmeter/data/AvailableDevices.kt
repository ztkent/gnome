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

class AvailableDevices {
    private val coroutineScope = CoroutineScope(Job() + Dispatchers.IO + SupervisorJob())
    private val semaphore = Semaphore(10) // Limit coroutine concurrency to 10 threads

    // Might be able to safely modify concurrently
    private val deviceList = CopyOnWriteArrayList<String>()
    private var lastChecked = Clock.System.now()
    private val scanningLock = Mutex()

    fun GetAvailableDevices(context: Context): List<String> {
        if (deviceList.size > 0 && (Clock.System.now() - lastChecked < 10.minutes)) {
            Log.d("GetAvailableDevices", "Cached device list: $deviceList")
            return deviceList
        }
        if (!scanningLock.isLocked) {
            coroutineScope.async {
                scanningLock.lock()
                fetchAvailableDevices(context)
            }
        }
        Log.d("GetAvailableDevices", "Device list: $deviceList")
        return deviceList
    }

    private suspend fun fetchAvailableDevices(context: Context) {
        // All available devices will be on our network, with a hostname: sunlight.local,
        // or likely the same octet as us. Check the hostname, then every option on the same local network as us.
        // look for a 200 response code at /id
        Log.d("fetchAvailableDevices", "Scanning for devices...")

        lastChecked = Clock.System.now()

        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkCapabilities =
            connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        val ip = getOwnIpAddress(context, connectivityManager, networkCapabilities)

        if (ip == null) {
            Log.d("fetchAvailableDevices", "Device IP is NULL, not connected to WIFI")
            scanningLock.unlock()
            return
        }
        Log.d("fetchAvailableDevices", "Device IP is $ip")

        Log.d("fetchAvailableDevices", "Checking for devices on this network...")
        val potentialIps = generatePotentialDeviceIps(ip)
        coroutineScope.async {
            val deferredHost = async {
                checkForDeviceResponse("", "sunlight.local")
            }
            deferredHost.await().let { foundDevice ->
                if (foundDevice) {
                    addDevice("sunlight.local")
                }
            }

            val deferredResults2 = potentialIps.map { potentialIp ->
                async {
                    semaphore.acquire() // Acquire a permit before starting
                    try {
                        val foundDevice = checkForDeviceResponse(potentialIp, "")
                        if (foundDevice) {
                            addDevice(potentialIp)
                        }
                    } finally {
                        semaphore.release() // Release the permit after finishing
                    }
                }
            }
            deferredResults2.awaitAll()
            Log.d("fetchAvailableDevices", "Devices found on this network: $deviceList")
            scanningLock.unlock()
        }
    }


    private fun addDevice(device: String) {
        if (!deviceList.contains(device)) {
            deviceList.add(device)
        }
    }

    private fun getOwnIpAddress(
        context: Context,
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

    private fun generatePotentialDeviceIps(ownIp: String): List<String> {
        val ipParts = ownIp.split(".").toMutableList()
        val potentialIps = mutableListOf<String>()
        for (i in 1..254) { // Check all possible values for the last octet
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
            connection.disconnect()
            return responseCode == HttpURLConnection.HTTP_OK
        } catch (e: Exception) {
            // Handle exceptions (e.g., timeout, connection refused)
            Log.e("checkForDeviceResponse", "Error checking device at $url: ${e.message}")
            return false
        }
    }

}
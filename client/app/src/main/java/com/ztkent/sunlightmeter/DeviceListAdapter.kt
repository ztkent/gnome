package com.ztkent.sunlightmeter

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class DeviceListAdapter() :
    RecyclerView.Adapter<DeviceListAdapter.ButtonViewHolder>() {
    private val deviceList = mutableListOf<String>("Device 1", "Device 2", "Device 3")
    class ButtonViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val button: Button = itemView.findViewById(R.id.buttonItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ButtonViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_button, parent, false) // Still using text_item.xml
        return ButtonViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ButtonViewHolder, position: Int) {
        holder.button.text = deviceList[position]
        // You can add button click listeners here if needed:
        holder.button.setOnClickListener {
            // Handle button click for item at position
            // For example:
            fetchAvailableDevices(holder.itemView.context)
            Toast.makeText(holder.itemView.context, "Button ${position + 1} clicked", Toast.LENGTH_SHORT).show()
        }
    }

    override fun getItemCount(): Int = deviceList.size
}

private fun fetchAvailableDevices(context: Context) {
    // All available devices will be on our network, with a hostname: sunlight.local,
    // or likely the same octet as us. Check the hostname, then every option on the same local network as us.
    // look for a 200 response code at /id
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val networkCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
    val ip = getOwnIpAddress(context, connectivityManager, networkCapabilities)

    if (ip == null) {
        Log.d("fetchAvailableDevices", "Device IP is NULL, not connected to WIFI")
        return
    }
    Log.d("fetchAvailableDevices", "Device IP is $ip")


    Log.d("fetchAvailableDevices", "Checking for devices at sunlight.local...")
    val found = checkForDeviceResponse(ip, "sunlight.local")
    if (found) {
        Log.d("fetchAvailableDevices", "Device at sunlight.local found!")
        return
    }

    Log.d("fetchAvailableDevices", "Checking for devices on this network...")
    val deviceList = mutableListOf<String>()
    val potentialIps = generatePotentialDeviceIps(ip)
    for (potentialIp in potentialIps) {
        val foundDevice = checkForDeviceResponse(potentialIp, "")
        if (foundDevice) {
            deviceList.add(potentialIp)
        }
    }
}

private fun getOwnIpAddress(context: Context, connectivityManager: ConnectivityManager, networkCapabilities: NetworkCapabilities?): String? {
    if (networkCapabilities != null &&
        networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    ) {
        val linkProperties = connectivityManager.getLinkProperties(connectivityManager.activeNetwork)
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

private fun checkForDeviceResponse(ip: String, host: String): Boolean{
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
            Log.e("fetchAvailableDevices", "Error checking device at $ip: ${e.message}")
            return false
        }
}
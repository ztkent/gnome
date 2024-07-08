package com.ztkent.sunlightmeter.ui.fragments.connectdevices

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ztkent.sunlightmeter.R
import com.ztkent.sunlightmeter.data.AvailableDevices
import com.ztkent.sunlightmeter.data.SunlightModel

class ConnectDevicesFragment : Fragment() {
    private val viewModel: SunlightModel by activityViewModels()

    private lateinit var recyclerView: RecyclerView
    private lateinit var refreshDevicesButton: Button
    private lateinit var recyclerViewHeader: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize the device list. Seems to be ready when we need it?
        viewModel.deviceHandler.GetAvailableDevices(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_connect_devices, container, false)
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize the view elements
        recyclerView = view.findViewById(R.id.availableDevicesRecyclerView)
        refreshDevicesButton = view.findViewById(R.id.refreshDevicesButton)
        recyclerViewHeader = view.findViewById(R.id.availableDevicesHeaderText)

        // Set up the button links
        refreshDevicesButton.setOnClickListener {
            // Update the available device list
            val availableDevices = populateAvailableDevices(viewModel.deviceHandler)

            // Notify the adapter about the changes
            val adapter = recyclerView.adapter as? AvailableDevicesListAdapter
            adapter?.updateAvailableDevices(availableDevices)
            adapter?.notifyDataSetChanged()
        }

        // Set the page content
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter =
            AvailableDevicesListAdapter(
                viewModel,
                currentlyAvailableDevices().toMutableList()
            )
    }

    private fun currentlyAvailableDevices(): List<String> {
        val currentlyAvailableDevices: MutableList<String> = mutableListOf()
        val availableDevices = viewModel.deviceHandler.GetAvailableDevices(requireContext())
        val connectedDevicesList =
            viewModel.connectedDevices.value?.toMutableList()
                ?: mutableListOf()

        for (device in availableDevices) {
            if (!connectedDevicesList.contains(device)) {
                currentlyAvailableDevices.add(device)
            }
        }
        return currentlyAvailableDevices
    }

    // Look for available devices for up to 5 seconds
    private fun populateAvailableDevices(deviceHandler: AvailableDevices): List<String> {
        val startTime = System.currentTimeMillis()
        val timeoutMillis = 5000
        val connectedDevicesList =
            viewModel.connectedDevices.value?.toMutableList()
                ?: mutableListOf()

        var availableDevices = deviceHandler.GetAvailableDevices(requireContext())
        while (availableDevices.isEmpty() && (System.currentTimeMillis() - startTime) < timeoutMillis) {
            availableDevices = deviceHandler.GetAvailableDevices(requireContext())
            Thread.sleep(500)
        }

        if (availableDevices.isEmpty()) {
            Log.d("populateAvailableDevices", "No devices found.")
        } else {
            Log.d("populateAvailableDevices", "Available devices: $availableDevices")
        }

        // Ignore any devices we're already connected to
        val unconnectedAvailableDevices = mutableListOf<String>()
        for (device in availableDevices) {
            if (!connectedDevicesList.contains(device)) {
                unconnectedAvailableDevices.add(device)
            }
        }

        return unconnectedAvailableDevices
    }
}
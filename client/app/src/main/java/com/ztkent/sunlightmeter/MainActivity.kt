package com.ztkent.sunlightmeter

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var linkDeviceButton: Button
    private lateinit var recyclerViewHeader: TextView

    private lateinit var connectedDevices: List<String>

    override fun onCreate(savedInstanceState: Bundle?) {
       // Inflate the view
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Initialize the view elements
        recyclerView = findViewById(R.id.buttonRecyclerView)
        linkDeviceButton = findViewById(R.id.linkDeviceButton)
        recyclerViewHeader = findViewById(R.id.recyclerHeaderText)
        connectedDevices = mutableListOf("Test String1", "Test String 2", "Test String 3")

        // Initialize the device list. Seems to be ready when we need it?
        val deviceHandler = AvailableDevices()
        deviceHandler.GetAvailableDevices(this)
        linkDeviceButton.setOnClickListener {
            linkDeviceButton.text = "Refresh Sunlight Meter Devices"
            recyclerViewHeader.text = "Available Sunlight Meter Devices"
            val availableDevices = populateAvailableDevices(deviceHandler)
            recyclerView.adapter = AvailableDevicesListAdapter(availableDevices)
        }

        // Button adapter is the main screen
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = ConnectedDevicesListAdapter(connectedDevices)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (recyclerView.adapter is AvailableDevicesListAdapter){
                    linkDeviceButton.text = "Link Sunlight Meter Device"
                    recyclerViewHeader.text = "Connected Devices"
                    recyclerView.adapter = ConnectedDevicesListAdapter(connectedDevices)
                } else {
                    // If it's not the DeviceListAdapter, let the default behavior handle it
                    if (!isEnabled) {
                        isEnabled = true
                    }
                }
            }
        })

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    // Look for available devices for up to 5 seconds
    private fun populateAvailableDevices(deviceHandler: AvailableDevices): List<String> {
        val startTime = System.currentTimeMillis()
        val timeoutMillis = 5000

        var availableDevices = deviceHandler.GetAvailableDevices(this)
        while (availableDevices.isEmpty() && (System.currentTimeMillis() - startTime) < timeoutMillis) {
            availableDevices = deviceHandler.GetAvailableDevices(this)
        }

        if (availableDevices.isEmpty()) {
            Log.d("populateAvailableDevices", "No devices found.")
        } else {
            Log.d("populateAvailableDevices", "Available devices: $availableDevices")
        }
        return availableDevices
    }
}

/*
Consider 'Live Data'
// ViewModel
class MyViewModel : ViewModel() {
    private val _data = MutableLiveData<String>()
    val data: LiveData<String> = _data

    fun updateData(newData: String) {
        _data.value = newData
    }
}

// Activity
class MyActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.my_activity)

        // Obtain ViewModel
        val viewModel = ViewModelProviders.of(this).get(MyViewModel::class.java)

        // Observe LiveData
        viewModel.data.observe(this, Observer { data ->
            // Update UI
            textView.text = data
        })
    }
}
*/
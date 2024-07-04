package com.ztkent.sunlightmeter

import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)


        // Initialize the device list. Maybe its ready when we need it?
        val deviceHandler = AvailableDevices()
        deviceHandler.GetAvailableDevices(this)

        setupHandlers(deviceHandler)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setupHandlers(deviceHandler: AvailableDevices) {
        val textDataList = mutableListOf("Test String 1", "Test String 2", "Test String 3")
        val linkDeviceButton: Button = findViewById(R.id.linkDeviceButton)
        val recyclerView: RecyclerView = findViewById(R.id.buttonRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = ButtonAdapter(textDataList)

        linkDeviceButton.setOnClickListener {
            populateAvailableDevices(deviceHandler)
            recyclerView.adapter = DeviceListAdapter()
        }
    }

    private fun populateAvailableDevices(deviceHandler: AvailableDevices) {
        var availableDevices = deviceHandler.GetAvailableDevices(this)
        while (availableDevices.isEmpty()) {
            availableDevices = deviceHandler.GetAvailableDevices(this)
        }
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
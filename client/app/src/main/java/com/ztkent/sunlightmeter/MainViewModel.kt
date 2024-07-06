package com.ztkent.sunlightmeter

import androidx.lifecycle.ViewModel

class MainViewModel : ViewModel() {
    var deviceHandler = AvailableDevices()
    var connectedDevices: List<String> =
        mutableListOf("Test String1", "Test String 2", "Test String 3")
}
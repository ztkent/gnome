package com.ztkent.sunlightmeter.data

import androidx.lifecycle.ViewModel

// Data available to all fragments via the MainActivity container
class SunlightModel : ViewModel() {
    val deviceHandler = AvailableDevices()
    var connectedDevices: MutableList<String> = mutableListOf()
}
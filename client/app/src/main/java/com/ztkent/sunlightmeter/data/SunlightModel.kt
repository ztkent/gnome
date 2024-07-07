package com.ztkent.sunlightmeter.data

import androidx.lifecycle.ViewModel
import com.ztkent.sunlightmeter.data.repository.Repository

// Data available to all fragments via the MainActivity container
class SunlightModel() : ViewModel() {
    val deviceHandler = AvailableDevices()
    var connectedDevices: MutableList<String> = mutableListOf()
    var repository: Repository? = null
}

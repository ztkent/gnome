package com.ztkent.sunlightmeter

import androidx.lifecycle.ViewModel

class MainViewModel : ViewModel() {
    val deviceHandler = AvailableDevices()
    var connectedDevices: MutableList<String> = mutableListOf()
}
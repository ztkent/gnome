package com.ztkent.sunlightmeter.data

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ztkent.sunlightmeter.data.repository.Repository
import kotlinx.coroutines.launch

// Data available to all fragments via the MainActivity container
class SunlightModel() : ViewModel() {
    val deviceHandler = AvailableDevices()
    private val _connectedDevices = MutableLiveData<List<String>>(emptyList())
    val connectedDevices: LiveData<List<String>> = _connectedDevices
    var repository: Repository? = null

    fun fetchConnectedDevices() {
        viewModelScope.launch {
            repository?.getDevices()?.collect { devices -> // Collect from the Flow
                val dbDevices = devices.map { it.ssid } // Extract ssid values
                updateConnectedDevices(dbDevices)
            }
        }
    }

    fun updateConnectedDevices(newDevices: List<String>) {
        _connectedDevices.value = newDevices
    }
}


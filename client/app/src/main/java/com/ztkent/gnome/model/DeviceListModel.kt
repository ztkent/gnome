package com.ztkent.gnome.model

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ztkent.gnome.SunlightActivity
import com.ztkent.gnome.data.AvailableDevices
import com.ztkent.gnome.data.Device
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Device List Model
open class DeviceListModel(sunlightActivity: SunlightActivity) : ViewModel() {
    val _devices = MutableStateFlow<DeviceLoadState>(DeviceLoadState.Loading)
    val devices: StateFlow<DeviceLoadState> = _devices.asStateFlow()
    val slActivity = sunlightActivity

    init {
        viewModelScope.launch {
            loadDevices()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            loadDevices()
        }
    }

    fun updateDevice(updatedDevice: Device, delay: Long = 500) {
        if (devices.value is DeviceLoadState.Success) {
            val currentDevices = (_devices.value as DeviceLoadState.Success).data
            val updatedDevices = currentDevices.map {
                if (it.addr == updatedDevice.addr) updatedDevice else it
            }
            _devices.value = DeviceLoadState.Loading // Optional: Show loading state briefly
            Thread.sleep(delay) // Max request time
            _devices.value = DeviceLoadState.Success(updatedDevices)
        }
    }

    private suspend fun loadDevices() {
        try {
            _devices.value = DeviceLoadState.Loading
            val availableDevices = AvailableDevices()
            val loadedDevices = availableDevices.getAvailableDevices(slActivity)
            for (device in loadedDevices) {
                Log.d("SunlightActivity", "Available device: $device")
            }
            _devices.value = DeviceLoadState.Success(loadedDevices)
        } catch (e: Exception) {
            _devices.value = DeviceLoadState.Error(e)
        }
    }

    fun getDeviceByAddr(addr: String): Device? {
        return if (devices.value is DeviceLoadState.Success) {
            val currentDevices = (devices.value as DeviceLoadState.Success).data
            currentDevices.find { it.addr == addr }
        } else {
            null
        }
    }
}
sealed class DeviceLoadState {
    data object Loading : DeviceLoadState()
    data class Success(val data: List<Device>) : DeviceLoadState()
    data class Error(val exception: Exception) : DeviceLoadState()
}
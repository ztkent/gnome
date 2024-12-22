package com.ztkent.gnome

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.ztkent.gnome.data.AvailableDevices
import com.ztkent.gnome.data.Conditions
import com.ztkent.gnome.data.Device
import com.ztkent.gnome.data.Errors
import com.ztkent.gnome.data.SignalStrength
import com.ztkent.gnome.data.Status
import com.ztkent.gnome.model.DeviceListModel
import com.ztkent.gnome.model.DeviceLoadState
import kotlinx.coroutines.launch

class MockAvailableDevices : AvailableDevices() {
    override suspend fun getAvailableDevices(context: Context): List<Device> {
        val numDevices = (1..10).random() // Generate a random number between 1 and 10
        return (1..numDevices).map {
            Device(
                addr = "192.168.1.$it",
                serviceName = "Gnome", // or "Sunlight Meter"
                outboundIp = "192.168.1.$it",
                macAddresses = listOf("00:11:22:33:44:$it"),
                signalStrength = SignalStrength(signalInt = -50 + it * 10, strength = (15..100).random()),
                conditions = Conditions(jobID = "job$it", lux = (0..15000).random()),
                status = Status(connected = true, enabled = it % 3 != 0),
                errors = Errors(signalStrength = if (it % 4 == 0) "Signal error" else "No errors")
            )
        }
    }
}
class DeviceListModelPreview : DeviceListModel(SunlightActivity()) {
    init {
        viewModelScope.launch {
            // Use the fake data for preview
            _devices.value = DeviceLoadState.Success(MockAvailableDevices().getAvailableDevices(SunlightActivity()))
        }
    }
}
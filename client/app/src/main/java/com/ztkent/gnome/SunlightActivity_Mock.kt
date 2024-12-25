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
        val luxRanges = listOf(
            0..2, 3..20, 20..49, 50..50, 51..79, 80..99, 100..149,
            150..319, 320..399, 400..999, 1000..9999, 10000..30000
        )

        return luxRanges.mapIndexed { index, range ->
            val lux = range.random() // Generate a random Lux value within the range
            Device(
                addr = "192.168.1.${index + 1}",
                serviceName = "Gnome",
                outboundIp = "192.168.1.${index + 1}",
                macAddresses = listOf("00:11:22:33:44:${index + 1}"),
                signalStrength = SignalStrength(signalInt = -50 + (index + 1) * 10, strength = (15..100).random()),
                conditions = Conditions(jobID = "job${index + 1}", lux = lux),
                status = Status(connected = true, enabled = true),
                errors = Errors()
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
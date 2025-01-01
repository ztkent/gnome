package com.ztkent.gnome.data

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import com.ztkent.gnome.SunlightActivity

fun ScanBluetoothDevices(slActivity: SunlightActivity) {
    val bluetoothManager = slActivity.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    val bluetoothAdapter = bluetoothManager.adapter
    if (bluetoothAdapter?.isEnabled == true) {
        val bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        val scanFilter = ScanFilter.Builder().build()

        // Check for Bluetooth permissions based on API level
        val hasBluetoothScanPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(
                slActivity,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // For API level 30 and below, BLUETOOTH_ADMIN is sufficient
            ActivityCompat.checkSelfPermission(
                slActivity,
                Manifest.permission.BLUETOOTH_ADMIN
            ) == PackageManager.PERMISSION_GRANTED
        }

        // Check for location permission (required for all API levels)
        val hasLocationPermission = ActivityCompat.checkSelfPermission(
            slActivity,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasBluetoothScanPermission && hasLocationPermission) {
            bluetoothLeScanner?.startScan(
                listOf(scanFilter),
                scanSettings,
                btCallback
            )
        } else {
            // Request the missing permissions
           slActivity.requestBluetoothPermissions()
        }
    } else {
        // Bluetooth is not enabled, handle accordingly
    }
}

private val btCallback = object : ScanCallback() {
    override fun onScanResult(callbackType: Int, result: ScanResult) {
        super.onScanResult(callbackType, result)
        val device = result.device
        // Process the discovered device (e.g., add it to a list)
    }

    override fun onBatchScanResults(results: List<ScanResult>) {
        super.onBatchScanResults(results)
        Log.d("BluetoothScan", "Batch scan results: ${results.size} devices found")
        // Process a batch of discovered devices
    }

    override fun onScanFailed(errorCode: Int) {
        super.onScanFailed(errorCode)
        Log.d("BluetoothScan", "Scan failed with error code: $errorCode")
        // Handle scan failure
    }
}
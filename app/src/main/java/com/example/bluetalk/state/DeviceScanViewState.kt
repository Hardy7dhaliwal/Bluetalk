package com.example.bluetalk.state

import com.example.bluetalk.model.DeviceInfo

sealed class DeviceScanViewState{
    object ActiveScan: DeviceScanViewState()
    class ScanResults(val scanResults: MutableMap<String, DeviceInfo>): DeviceScanViewState()
    class Error(val message: String): DeviceScanViewState()
    object AdvertisementNotSupported: DeviceScanViewState()
}
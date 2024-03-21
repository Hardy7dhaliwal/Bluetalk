package com.example.bluetalk.bluetooth.client_repository

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import com.example.bluetalk.model.DeviceInfo
import com.example.bluetalk.spec.SERVICE_UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import kotlin.coroutines.resumeWithException

@SuppressLint("MissingPermission")
class ScannerRepository(
     val context: Context,
    private val bluetoothAdapter: BluetoothAdapter,
    private val scope: CoroutineScope
) {
    private val _devices = MutableSharedFlow<DeviceInfo>()
    val devices = _devices.asSharedFlow().distinctUntilChanged()

    private var currentScanCallback: ScanCallback? = null
    private val bluetoothLeScanner: BluetoothLeScanner by lazy {
        bluetoothAdapter.bluetoothLeScanner
            ?: throw NullPointerException("Bluetooth not initialized")
    }
    private fun getUserID(uuids: List<ParcelUuid>): UUID? {
        uuids.forEach { uuid->
            if(uuid.uuid != SERVICE_UUID){
                return uuid.uuid
            }
        }
        return null
    }

    suspend fun searchDevices() {
        println("Searching Started")
        if (currentScanCallback == null) {
            suspendCancellableCoroutine<Unit> { continuation ->
                currentScanCallback = object : ScanCallback() {
                    override fun onScanResult(callbackType: Int, result: ScanResult?) {
                        result?.let { r ->
                            if (r.scanRecord?.getServiceData(ParcelUuid(SERVICE_UUID)) != null) {

                                val serviceUUIDs = r.scanRecord?.serviceUuids
                                val userId = serviceUUIDs?.let { getUserID(it) }
                                val serviceData =
                                    r.scanRecord?.getServiceData(ParcelUuid(SERVICE_UUID))
                                var username = ""
                                serviceData?.let { data ->
                                    username = String(data, Charsets.UTF_8)
                                }
                                result.device?.let { device ->
                                    val deviceInfo = DeviceInfo(device, username, userId.toString())
                                    // Emit the deviceInfo to the flow
                                    scope.launch {
                                        _devices.emit(deviceInfo)
                                    }
                                }
                            }
                        }
                    }

                    override fun onScanFailed(errorCode: Int) {
                        continuation.resumeWithException(ScanningException(errorCode))
                    }
                }
                continuation.invokeOnCancellation {
                    bluetoothLeScanner.stopScan(currentScanCallback)
                    currentScanCallback = null
                }

                val scanSettings = ScanSettings.Builder()
                    .setReportDelay(0)
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                    .build()

                val scanFilters = listOf(
                    ScanFilter.Builder()
                        .build()
                )

                bluetoothLeScanner.startScan(scanFilters, scanSettings, currentScanCallback)
            }
        }
    }

    fun stopScan() {
        currentScanCallback?.let {
            bluetoothLeScanner.stopScan(it)
            currentScanCallback = null // Clear the callback reference
        }
    }



}


data class ScanningException(val errorCode: Int): Exception() {

    override val message: String?
        get() = when (errorCode) {
            ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> "Scan not supported"
            ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "Scan registration failed"
            ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> "Internal scanning error"
            ScanCallback.SCAN_FAILED_ALREADY_STARTED -> "Scan already started"
            ScanCallback.SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES -> "Out of hardware resources"
            ScanCallback.SCAN_FAILED_SCANNING_TOO_FREQUENTLY -> "Scanning too frequently"
            else -> super.message
        }
}
package com.example.bluetalk.viewModel

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.Build
import android.os.Handler
import android.os.ParcelUuid
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.bluetalk.database.ChatDao
import com.example.bluetalk.database.ChatDatabase
import com.example.bluetalk.model.DeviceInfo
import com.example.bluetalk.model.User
import com.example.bluetalk.spec.SERVICE_UUID
import com.example.bluetalk.state.DeviceScanViewState
import kotlinx.coroutines.launch
import java.util.UUID

private const val TAG = "ScanDeviceViewModel"
// 30 second scan period
private const val SCAN_PERIOD = 30000L

@SuppressLint("MissingPermission", "LogNotTimber")
class ScanDeviceViewModel (app: Application):AndroidViewModel(app){

    //LiveData for sending the view state to the UsersFragment
    private val _viewState = MutableLiveData<DeviceScanViewState>()
    val viewState = _viewState as LiveData<DeviceScanViewState>

    //String is the address of the bluetooth device
    private val scanResults = mutableMapOf<String, DeviceInfo>()
    private val _foundDevice = MutableLiveData<DeviceInfo>()
    val foundDevice = _foundDevice as LiveData<DeviceInfo>

    private val adapter: BluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

    private var scanner: BluetoothLeScanner? = null

    private var scanCallback: DeviceScanCallback? = null
    private val scanFilters: List<ScanFilter>
    private val scanSettings: ScanSettings

    private var database: ChatDatabase = ChatDatabase.getDatabase(this.getApplication())
    private var chatDao: ChatDao = database.chatDao()

    init {
        // Setup scan filters and settings
        scanFilters = buildScanFilters()
        scanSettings = buildScanSettings()
        scanResults.clear()
        // Start a scan for BLE devices
    }



    override fun onCleared() {
        super.onCleared()
        stopScanning()
        scanResults.clear()

    }


    fun startScan() {
        scanResults.clear()
        // If advertisement is not supported on this device then other devices will not be able to
        // discover and connect to it.
        if (!adapter.isMultipleAdvertisementSupported) {
            _viewState.value = DeviceScanViewState.AdvertisementNotSupported
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                return
            }
        }

        if (scanCallback == null) {
            scanner = adapter.bluetoothLeScanner
            Log.d(TAG, "Start Scanning")
            // Update the UI to indicate an active scan is starting
            _viewState.value = DeviceScanViewState.ActiveScan

            // Stop scanning after the scan period
           Handler().postDelayed({ stopScanning() }, SCAN_PERIOD)

            // Kick off a new scan
            scanCallback = DeviceScanCallback()
            scanner?.startScan(scanFilters, scanSettings, scanCallback)
        } else {
            Log.d(TAG, "Already scanning")
        }
    }


    fun stopScanning() {
        Log.d(TAG, "Stopping Scanning")
        scanner?.stopScan(scanCallback)
        scanCallback = null
        // return the current results
        _viewState.value = DeviceScanViewState.ScanResults(scanResults)
    }

    /**
     * Return a List of [ScanFilter] objects to filter by Service UUID.
     */
    private fun buildScanFilters(): List<ScanFilter> {
        val builder = ScanFilter.Builder()
        val filter = builder.build()
        return listOf(filter)
    }

    /**
     * Return a [ScanSettings] object set to use low power (to preserve battery life).
     */
    private fun buildScanSettings(): ScanSettings {
        return ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setReportDelay(0L)
            .build()
    }

    /**
     * Custom ScanCallback object - adds found devices to list on success, displays error on failure.
     */
    private inner class DeviceScanCallback : ScanCallback() {
        override fun onBatchScanResults(results: List<ScanResult>) {
            super.onBatchScanResults(results)
            for (item in results) {
                val userId = item.scanRecord?.serviceUuids?.let { getUserID(it) }
                val serviceData = item.scanRecord?.getServiceData(ParcelUuid(SERVICE_UUID))
                var username = ""
                serviceData?.let { data ->
                    username = String(data, Charsets.UTF_8)
                }
                item.device?.let { device ->
                    val deviceInfo = DeviceInfo(device, username,userId.toString())
                    scanResults[device.address] = deviceInfo
                }
            }
            _viewState.value = DeviceScanViewState.ScanResults(scanResults)
        }

        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            if(result.scanRecord?.getServiceData(ParcelUuid(SERVICE_UUID))!=null){
                val serviceUUIDs = result.scanRecord?.serviceUuids
                val userId = serviceUUIDs?.let { getUserID(it) }
                val serviceData = result.scanRecord?.getServiceData(ParcelUuid(SERVICE_UUID))
                var username = ""
                serviceData?.let { data ->
                    username = String(data, Charsets.UTF_8)
                }
                result.device?.let { device ->
                    val deviceInfo = DeviceInfo(device, username, userId.toString() )
                    scanResults[device.address] = deviceInfo
                    _foundDevice.value = deviceInfo
                    viewModelScope.launch{
                        if(chatDao.userExists(deviceInfo.id)){
                            chatDao.updateSpecificFields(deviceInfo.id,username,device.address)
                        }else {
                            chatDao.insertUser(
                                User(
                                    deviceInfo.id,
                                    deviceInfo.username,
                                    deviceInfo.device.address
                                )
                            )
                        }
                    }
                }
                _viewState.value = DeviceScanViewState.ScanResults(scanResults)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            // Send error state to the fragment to display
            val errorMessage = "Scan failed with error: $errorCode"
            _viewState.value = DeviceScanViewState.Error(errorMessage)
        }
    }

    private fun getUserID(uuids: List<ParcelUuid>): UUID? {
        uuids.forEach { uuid->
            if(uuid.uuid != SERVICE_UUID){
                return uuid.uuid
            }
        }
        return null
    }

}


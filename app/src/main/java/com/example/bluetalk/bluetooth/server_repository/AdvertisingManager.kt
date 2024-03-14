package com.example.bluetalk.bluetooth.server_repository

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.os.ParcelUuid
import android.util.Log
import com.example.bluetalk.spec.SERVICE_UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import kotlin.coroutines.resumeWithException

data class AdvertisingException(val errorCode: Int): Exception()
@SuppressLint("MissingPermission")
class AdvertisingManager(
    private val appID: UUID,
    private val userName:String,
    private val bluetoothAdapter: BluetoothAdapter
) {

    @SuppressLint("HardwareIds")
    val address: String? = bluetoothAdapter.address
    private val bluetoothLeAdvertiser: BluetoothLeAdvertiser by lazy {
        bluetoothAdapter.bluetoothLeAdvertiser
            ?: throw NullPointerException("Bluetooth not initialized")
    }
    private var advertisingCallback: AdvertiseCallback? = null


    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun startAdvertising() = suspendCancellableCoroutine { continuation ->
        advertisingCallback = object : AdvertiseCallback() {

            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                continuation.resume(Unit) { }
            }

            override fun onStartFailure(errorCode: Int) {
                continuation.resumeWithException(AdvertisingException(errorCode))
            }
        }

        continuation.invokeOnCancellation {
            bluetoothLeAdvertiser.stopAdvertising(advertisingCallback)
        }

        val advertisingSettings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
            .setTimeout(0)
            .build()
        val user = userName.toByteArray(Charsets.UTF_8)
        Log.d("Advertise: ","User: $userName")
        val advertisingData = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .addServiceData(ParcelUuid(SERVICE_UUID),user)
            .setIncludeDeviceName(false)
            .build()

        val scanResponse = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(appID))
            .build()

        bluetoothLeAdvertiser.startAdvertising(
            advertisingSettings,
            advertisingData,
            scanResponse,
            advertisingCallback
        )
    }

    fun stopAdvertising() {
        bluetoothLeAdvertiser.stopAdvertising(
            advertisingCallback
        )
    }
}

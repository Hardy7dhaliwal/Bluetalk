package com.example.bluetalk.ui

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import com.example.bluetalk.database.ChatDao
import com.example.bluetalk.database.ChatDatabase
import com.example.bluetalk.databinding.FragmentProxyBinding
import com.example.bluetalk.model.DeviceInfo
import com.example.bluetalk.model.ProxyPacket
import com.example.bluetalk.model.UUIDManager
import com.example.bluetalk.viewModel.ScanDeviceViewModel
import com.example.bluetalk.viewModel.SharedViewModel
import java.util.LinkedList
import java.util.Queue
import java.util.UUID

data class Operation(val address:String, val device: BluetoothDevice)
private const val TAG = "ProxyFragment"
class ProxyFragment(val dst: String) : DialogFragment() {
    private lateinit var proxyPacket:ProxyPacket
    private var _binding: FragmentProxyBinding? = null
    private val binding
        get() = _binding!!
    private lateinit var appUUID: UUID
    private val operationQueue: Queue<Operation> = LinkedList()
    private lateinit var model: SharedViewModel
    private val viewModel: ScanDeviceViewModel by viewModels()
    private var database: ChatDatabase? = null
    private var chatDao: ChatDao? = null
    private var processedUsers = mutableListOf<String>()

    private val newDeviceObserver = Observer<DeviceInfo> { device ->
        val bAdapter = (requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        val d = bAdapter.getRemoteDevice(device.device.address)
        if(device.id != dst){

        }

    }


    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onStart() {
        super.onStart()
        appUUID = UUIDManager.getStoredUUID(requireContext())
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = ChatDatabase.getDatabase(requireContext())
        chatDao= database!!.chatDao()

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProxyBinding.inflate(inflater,container,false)
        return binding.root
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.stopScanning()
        //ProxyServer.disconnectAll()
        //stop
    }
}
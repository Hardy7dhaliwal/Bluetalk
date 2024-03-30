package com.example.bluetalk.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.bluetalk.adapter.OnDeviceSelectClickListener
import com.example.bluetalk.adapter.UserScanListAdapter
import com.example.bluetalk.bluetooth.BluetalkServer
import com.example.bluetalk.database.ChatDao
import com.example.bluetalk.database.ChatDatabase
import com.example.bluetalk.databinding.FragmentUsersBinding
import com.example.bluetalk.model.DeviceInfo
import com.example.bluetalk.model.User
import com.example.bluetalk.state.DeviceScanViewState
import com.example.bluetalk.viewModel.ScanDeviceViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID

private const val TAG = "UsersScanFragment"
@SuppressLint("LogNotTimber")
class UsersScanFragment : Fragment(), OnDeviceSelectClickListener {

    private var wasConnected = false
    private var _binding: FragmentUsersBinding? = null
    private val binding
        get() = _binding!!

    private val viewModel: ScanDeviceViewModel by viewModels()
    private var database: ChatDatabase? = null
    private var chatDao: ChatDao? = null
    private var progressBar: ProgressBar? =null

    private var requestedUser:DeviceInfo?=null
    private lateinit var appUUID: UUID

    private val <T> T.exhaustive: T
        get() = this
    private val viewStateObserver = Observer<DeviceScanViewState> { state ->
        when (state){
            is DeviceScanViewState.ActiveScan -> showLoading()
            is DeviceScanViewState.ScanResults -> showResults(state.scanResults)
            is DeviceScanViewState.Error -> showError(state.message)
            is DeviceScanViewState.AdvertisementNotSupported -> showAdvertisingError()
        }.exhaustive
    }

    @SuppressLint("MissingPermission")
    override fun onDeviceClick(device: DeviceInfo) {
        binding.progressBar.visibility=View.GONE
        Timber.tag(TAG).d("Device Clicked ${device.username}")
        binding.connectionProgress.visibility=View.VISIBLE
        viewModel.stopScanning()
        requestedUser = device
        val bAdapter = (requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        val d = bAdapter.getRemoteDevice(device.device.address)
        BluetalkServer.connectUser(d)
        lifecycleScope.launch(Dispatchers.Main){
            delay(1500)
            proceed()
        }
    }

    private val userScanAdapter by lazy{
        UserScanListAdapter(requireContext(), this)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        database = ChatDatabase.getDatabase(requireContext())
        chatDao = database!!.chatDao()
        // Inflate the layout for this fragment
        _binding = FragmentUsersBinding.inflate(inflater, container, false)
        progressBar = binding.progressBar
        binding.recyclerViewBluetoothUsers.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = userScanAdapter
        }
        return binding.root
    }
    @RequiresApi(Build.VERSION_CODES.S)
    fun hasRequiredRuntimePermissions(): Boolean {
        val requiredPermissions = listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.RECORD_AUDIO
        )

        return requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED
        }
    }


    @RequiresApi(Build.VERSION_CODES.S)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if(hasRequiredRuntimePermissions()){
            binding.connectionProgress.visibility = View.GONE
            userScanAdapter.clear()
            viewModel.startScan()
            viewModel.viewState.observe(viewLifecycleOwner, viewStateObserver)
        }else{
            Toast.makeText(requireContext(),"Need Bluetooth Permissions to start Scan",Toast.LENGTH_LONG).show()
        }
    }

    private fun proceed(){
        wasConnected=true
        if(requestedUser!=null) {
            lifecycleScope.launch(Dispatchers.IO) {
                val user =
                    requestedUser?.device?.address?.let {
                        User(requestedUser!!.id,requestedUser!!.username,it )
                    }
                Log.d(TAG, "onDeviceClick: Inserting User $user.uuid")
            }
            binding.connectionProgress.visibility = View.GONE
            val action =
                UsersScanFragmentDirections.actionUserScanFragmentToChatFragment(
                    requestedUser?.device!!.address, requestedUser!!.id, requestedUser!!.username
                )
            findNavController().navigate(action)
        }
    }

    private fun showLoading(){
        Log.d(TAG, "showLoading")
        progressBar?.visibility = View.VISIBLE
    }

    private fun showResults(scanResults: Map<String, DeviceInfo>){
        if(scanResults.isNotEmpty()){
            progressBar?.visibility = View.GONE
            userScanAdapter.updateItems(scanResults.values.toList())
        }else{
            Log.d(TAG, "Empty SCan List")
            showLoading()
        }
    }

    private fun showError(message: String){
        Log.d(TAG, "showError")
        Toast.makeText(requireContext(),"Scan Failed. Try Again!",Toast.LENGTH_LONG).show()
        Log.d(TAG, message)
    }

    private fun showAdvertisingError(){
        Log.d(TAG, "showAdvertisingError")
    }

    override fun onStop() {
        super.onStop()

        requestedUser=null
        viewModel.stopScanning()
        userScanAdapter.clear()
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.stopScanning()
        userScanAdapter.clear()
    }

}
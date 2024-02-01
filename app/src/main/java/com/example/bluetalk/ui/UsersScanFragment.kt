package com.example.bluetalk.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.bluetalk.adapter.OnDeviceSelectClickListener
import com.example.bluetalk.adapter.UserScanListAdapter
import com.example.bluetalk.bluetooth.ChatServer
import com.example.bluetalk.bluetooth.ScanDeviceViewModel
import com.example.bluetalk.database.ChatDao
import com.example.bluetalk.database.ChatDatabase
import com.example.bluetalk.databinding.FragmentUsersBinding
import com.example.bluetalk.model.Message
import com.example.bluetalk.model.User
import com.example.bluetalk.state.DeviceScanViewState

private const val TAG = "UsersScanFragment"

class UsersScanFragment : Fragment(), OnDeviceSelectClickListener {

    private var _binding: FragmentUsersBinding? = null
    private val binding
        get() = _binding!!

    private val viewModel: ScanDeviceViewModel by viewModels()
    private var database: ChatDatabase? = null
    private var chatDao: ChatDao? = null
    private val <T> T.exhaustive: T
        get() = this

    private val viewStateObserver = Observer<DeviceScanViewState> { state ->
        when (state){
            is DeviceScanViewState.ActiveScan -> println("ActiveScan")
            is DeviceScanViewState.ScanResults -> showResults(state.scanResults)
            is DeviceScanViewState.Error -> showError(state.message)
            is DeviceScanViewState.AdvertisementNotSupported -> showAdvertisingError()
        }.exhaustive
    }

    @SuppressLint("MissingPermission")
    override fun onDeviceClick(device: BluetoothDevice) {
        ChatServer.setCurrentChatConnection(device)
        chatDao?.insertUser(User(device.address, device.name))
        findNavController().popBackStack()
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

        binding.recyclerViewBluetoothUsers.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = userScanAdapter
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.viewState.observe(viewLifecycleOwner, viewStateObserver)
    }

    private fun showLoading(){
        Log.d(TAG, "showLoading")
    }

    private fun showResults(scanResults: Map<String, BluetoothDevice>){
        if(scanResults.isNotEmpty()){
            userScanAdapter.updateItems(scanResults.values.toList())
        }else{
            Log.d(TAG, "Empty SCan List")
            showLoading()
        }
    }

    private fun showError(message: String){
        Log.d(TAG, "showError")
        Log.d(TAG, message)

    }

    private fun showAdvertisingError(){
        Log.d(TAG, "showAdvertisingError")
    }
}
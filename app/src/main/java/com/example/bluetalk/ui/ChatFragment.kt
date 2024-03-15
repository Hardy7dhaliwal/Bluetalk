package com.example.bluetalk.ui

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.bluetalk.R
import com.example.bluetalk.adapter.MessageListAdapter
import com.example.bluetalk.bluetooth.BluetalkServer
import com.example.bluetalk.database.ChatDao
import com.example.bluetalk.database.ChatDatabase
import com.example.bluetalk.databinding.ChatLayoutBinding
import com.example.bluetalk.model.UUIDManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import no.nordicsemi.android.ble.ktx.state.ConnectionState
import java.util.UUID

private const val TAG = "ChatFragment"

@SuppressLint("LogNotTimber")
class ChatFragment: Fragment() {
    private var isConnected = false
    private val args: ChatFragmentArgs by navArgs()

    private var _binding: ChatLayoutBinding?=null

    private val binding: ChatLayoutBinding
        get() = _binding!!

    private var database: ChatDatabase? = null
    private var chatDao: ChatDao? = null

    private lateinit var messageListAdapter: MessageListAdapter
    private lateinit var appUUID: UUID


    private val inputMethodManager by lazy {
        requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private val proxyObserver = Observer<Boolean>{
        Log.d(TAG,"Found Proxy Hurray!!!")
        BluetalkServer.exchangeKeys()
        chatOneToProxy(args.deviceAddress)
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
        // Inflate the layout for this fragment
        messageListAdapter = MessageListAdapter(requireContext())
        _binding = ChatLayoutBinding.inflate(inflater, container, false)
        binding.chatRecyclerView.layoutManager = LinearLayoutManager(context)
        binding.chatRecyclerView.adapter = messageListAdapter

        return binding.root
    }


    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationIcon(androidx.constraintlayout.widget.R.drawable.abc_ic_ab_back_material)
        binding.toolbar.setNavigationOnClickListener{
            findNavController().navigate(R.id.action_chatFragment_to_conversations)
        }
        binding.toolbar.title = args.name
        isConnected=false
        val bAdapter = (requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        val d = bAdapter.getRemoteDevice(args.deviceAddress)
        BluetalkServer.foundPath.observe(viewLifecycleOwner,proxyObserver)
        loadMessagesFromDatabase(args.id)
        //chatThroughProxy()
        viewLifecycleOwner.lifecycleScope.launch {
            // Using viewLifecycleOwner.lifecycleScope to tie the collection to the Fragment's view lifecycle
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                getConnectionStateForDevice(args.deviceAddress).collect {state->
                    when (state) {
                        is ConnectionState.Connecting -> {println("Connecting in ChatFragment")}
                        is ConnectionState.Ready -> {
                            BluetalkServer.exchangeKeys(args.deviceAddress,args.id)
                            binding.walkieTalkieButton.setOnClickListener{
                                launch(Dispatchers.Main) {
                                    val dialog = WalkieTalkie(args.deviceAddress)
                                    dialog.show(parentFragmentManager, "Walkie Talkie")
                                }
                            }
                            isConnected=true
                            Log.d(TAG,"Connected in ChatFragment")
                            chatOneToOne(d.address)
                        }
                        is ConnectionState.Disconnected -> {
                            binding.walkieTalkieButton.setOnClickListener{
                                launch(Dispatchers.Main) {
                                    Toast.makeText(requireContext(),"You must be connected First.",Toast.LENGTH_SHORT).show()
                                }

                            }
                            showDisconnected(d)
                        }
                        else -> {}
                    }
                }
            }
        }
    }
    private fun getConnectionStateForDevice(deviceId: String): Flow<ConnectionState?> {
        return BluetalkServer.connectionStates
            .map { statesMap -> statesMap[deviceId] }
            .distinctUntilChanged() // Only emit when the state actually changes
    }


    @SuppressLint("MissingPermission")
    private fun loadMessagesFromDatabase(uuid: String) {
        // Fetch messages from the database using ChatDao
        chatDao?.getMessagesForUser(uuid)?.observe(viewLifecycleOwner) { messages ->
            messageListAdapter.submitList(messages)
            binding.chatRecyclerView.scrollToPosition(messageListAdapter.itemCount - 1)
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onStart() {
        super.onStart()
        Log.d(TAG,"Requested: ${args.name} ${args.id}")
        appUUID = UUIDManager.getStoredUUID(requireContext())
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun chatOneToOne(device: String){
        binding.toolbar.subtitle = "Connected"
        Log.d(TAG,"chatWith: Getting User $device ONE-ON-ONE")
        binding.buttonSend.setOnClickListener{
            val message = binding.inputMessage.text.toString()
            if(message.isNotEmpty()){
                val header = "$appUUID ${args.id} ${UUID.randomUUID()} ${args.name}"
                lifecycleScope.launch(Dispatchers.IO) {
                    BluetalkServer.sendMessage(args.deviceAddress,"0 $header\n$message")
                }
                binding.inputMessage.setText("")
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun chatOneToProxy(device: String){
        binding.toolbar.subtitle = "Connected"
        Log.d(TAG,"chatWith: Getting User $device ONE-ON-PROXY")
        binding.buttonSend.setOnClickListener{
            val message = binding.inputMessage.text.toString()
            if(message.isNotEmpty()){
                val header = "$appUUID ${args.id} ${UUID.randomUUID()} ${args.name}"
                lifecycleScope.launch(Dispatchers.IO) {
                    BluetalkServer.broadcastMessage(args.deviceAddress,"1 $header\n$message")
                }
                binding.inputMessage.setText("")
            }
        }
    }

    private fun showDisconnected(d: BluetoothDevice) {
        binding.toolbar.subtitle = "Disconnected"
        Toast.makeText(requireContext(),"!Connection Disconnected.", Toast.LENGTH_SHORT).show()
        hideKeyboard()
        Log.d(TAG,"ShowDisconnected: ")
        if(isConnected){
            lifecycleScope.launch(Dispatchers.Main) {
                showProxyDialog()
            }
        }else {
            lifecycleScope.launch {
                delay(10000)
                if(!isConnected) {
                    if (BluetalkServer.clientConnections[d.address] != null) {
                        launch(Dispatchers.Main) {
                            showProxyDialog()
                        }
                    }
                }
            }
        }
    }

    private fun hideKeyboard(){
        inputMethodManager.hideSoftInputFromWindow(binding.root.windowToken,0)
    }


    private fun showProxyDialog() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Connection Failed")
        builder.setMessage("Do you want to proceed with a proxy connection? This might take some time.")

        builder.setPositiveButton("Yes") { dialog,_ ->
            val header = "$appUUID ${args.id} ${UUID.randomUUID()} ${args.name}"
            lifecycleScope.launch(Dispatchers.IO) {
                BluetalkServer.sendMessage(args.deviceAddress,"1 $header\n ")
            }
        }

        builder.setNegativeButton("No") { dialog,_ ->
            dialog.dismiss()
            binding.buttonSend.setOnClickListener{
                Toast.makeText(requireContext(),"You are not Connected!!",Toast.LENGTH_SHORT)
                val message = binding.inputMessage.text.toString()
                binding.inputMessage.setText("")
            }
            // Handle the negative action if necessary
        }

        val dialog: AlertDialog = builder.create()
        dialog.show()
    }

}
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
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.bluetalk.R
import com.example.bluetalk.adapter.MessageListAdapter
import com.example.bluetalk.bluetooth.BluetalkServer
import com.example.bluetalk.database.ChatDao
import com.example.bluetalk.database.ChatDatabase
import com.example.bluetalk.databinding.ChatLayoutBinding
import com.example.bluetalk.model.Message
import com.example.bluetalk.model.UUIDManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

private const val TAG = "ChatFragment"

@SuppressLint("LogNotTimber")
class ChatFragment: Fragment() {
    private var wasConnected = false
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

//    private val proxyObserver = Observer<Boolean>{
//        chatThroughProxy()
//    }
//
//    private fun chatThroughProxy() {
//        binding.buttonSend.setOnClickListener{
//            val message = binding.inputMessage.text.toString()
//            if(message.isNotEmpty()){
//                Log.w(TAG,"Sending Message")
//                BluetalkServer.sendMessageUsingProxy("$appUUID ${args.id}\n$message")
//                binding.inputMessage.setText("")
//            }
//        }
//    }


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
        binding.walkieTalkieButton.setOnClickListener{
            val dialog = WalkieTalkie(args.deviceAddress)
            dialog.show(parentFragmentManager, "Walkie Talkie")
        }
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
        wasConnected=false
        val bAdapter = (requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
                            val d = bAdapter.getRemoteDevice(args.deviceAddress)
        loadMessagesFromDatabase(args.id)
        //chatThroughProxy()
        chatOneToOne(d)
//        viewLifecycleOwner.lifecycleScope.launch {
//            // Using viewLifecycleOwner.lifecycleScope to tie the collection to the Fragment's view lifecycle
//            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
//


//                BluetalkServer.connectionState.collect { state ->
//                    when (state) {
//                        is ConnectionState.Connecting -> {println("Connecting in ChatFragment")}
//                        is ConnectionState.Ready -> {
//                            wasConnected=true
//                            Log.d(TAG,"Connected in ChatFragment")
//                            val bAdapter = (requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
//                            val d = bAdapter.getRemoteDevice(args.deviceAddress)
//                            loadMessagesFromDatabase(args.id)
//                            chatOneToOne(d)
//                        }
//                        is ConnectionState.Disconnected -> {
//                            showDisconnected(d)
//                        }
//                        else -> {}
//                    }
//                }
//            }
//        }
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
        //BluetalkServer.pathFound.observe(viewLifecycleOwner,proxyObserver)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun chatOneToOne(device: BluetoothDevice){
        val bAdapter = (requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        val d = bAdapter.getRemoteDevice(device.address)       //resolve public mac address of the device.
        lifecycleScope.launch(Dispatchers.IO) {
           Log.d(TAG,"chatWith: Getting User $d.address")
        }
        binding.buttonSend.setOnClickListener{
            val message = binding.inputMessage.text.toString()
            if(message.isNotEmpty()){
                val header = "$appUUID ${args.id} ${UUID.randomUUID()} ${args.name}"
               //BluetalkServer.sendMessage(args.deviceAddress,"$header\n$message")
                lifecycleScope.launch(Dispatchers.IO) {
                    BluetalkServer.broadcastMessage("$header\n$message")
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
        if(wasConnected){
            showProxyDialog()
        }
        lifecycleScope.launch {
            delay(10000)
            if(BluetalkServer.clientConnections[d.address]!=null){
                showProxyDialog()
            }
        }
    }

    private fun hideKeyboard(){
        inputMethodManager.hideSoftInputFromWindow(binding.root.windowToken,0)
    }

    override fun onStop() {
        super.onStop()
       // Log.d(TAG,"Disconnecting....")
       // BluetalkServer.disconnectFrom(args.deviceAddress)
    }

    private fun insertMessageInDb(message: Message){
       lifecycleScope.launch(Dispatchers.IO) {
            chatDao?.insertMessage(message)
        }
    }

    private fun showProxyDialog() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Connection Failed")
        builder.setMessage("Do you want to proceed with a proxy connection? This might take some time.")

        builder.setPositiveButton("Yes") { dialog,_ ->
            //binding.connectionProgress.visibility = View.VISIBLE
        }

        builder.setNegativeButton("No") { dialog,_ ->
            dialog.dismiss()
            // Handle the negative action if necessary
        }

        val dialog: AlertDialog = builder.create()
        dialog.show()
    }

}
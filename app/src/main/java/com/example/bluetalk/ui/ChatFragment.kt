package com.example.bluetalk.ui

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.bluetalk.adapter.MessageListAdapter
import com.example.bluetalk.bluetooth.ChatServer
import com.example.bluetalk.database.ChatDao
import com.example.bluetalk.database.ChatDatabase
import com.example.bluetalk.databinding.ChatLayoutBinding
import com.example.bluetalk.model.Message
import com.example.bluetalk.state.UserConnectionState

private const val TAG = "ChatFragment"

class ChatFragment: Fragment() {

    private var _binding: ChatLayoutBinding?=null

    private val binding: ChatLayoutBinding
        get() = _binding!!

    private var database: ChatDatabase? = ChatDatabase.getDatabase(requireContext())
    private var chatDao: ChatDao? = database!!.chatDao()

    private lateinit var messageListAdapter: MessageListAdapter
    private lateinit var recyclerView: RecyclerView

    private val deviceConnectionObserver = Observer<UserConnectionState> { state ->
        when(state) {
            is UserConnectionState.Connected -> {
                val device = state.device
                loadMessagesFromDatabase(device.address)
                Log.d(TAG, "Gatt connection observer: have device $device")
                chatWith(device)
            }
            is UserConnectionState.Disconnected -> {
                showDisconnected()
            }
        }
    }

    private val connectionRequestObserver = Observer<BluetoothDevice> { device ->
        Log.d(TAG, "Connection request observer: have device $device")
        ChatServer.setCurrentChatConnection(device)
    }

    private val messageObserver = Observer<Message> { message ->
        Log.d(TAG, "Have message ${message.content}")
        messageListAdapter.addMessage(message)
    }

    private val inputMethodManager by lazy {
        requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        _binding = ChatLayoutBinding.inflate(inflater, container, false)
        binding.chatRecyclerView.layoutManager = LinearLayoutManager(context)
        binding.chatRecyclerView.adapter = messageListAdapter
        showDisconnected()
        return binding.root

    }

    private fun loadMessagesFromDatabase(userId: String) {
        // Fetch messages from the database using ChatDao
        chatDao?.getMessagesForUser(userId)?.observe(viewLifecycleOwner, Observer { messages ->
            // Update UI with messages retrieved from the database
            messageListAdapter.submitList(messages)
        })
    }

    override fun onStart() {
        super.onStart()

        //requireActivity().setTitle(R.string.chat_title)
        ChatServer.connectionRequest.observe(viewLifecycleOwner, connectionRequestObserver)
        ChatServer.deviceConnection.observe(viewLifecycleOwner, deviceConnectionObserver)
        ChatServer.messages.observe(viewLifecycleOwner, messageObserver)
    }



    private fun chatWith(device: BluetoothDevice){
        binding.toolbar.subtitle = "Connected with ${chatDao?.getUserName(device.address)}"
        binding.buttonSend.setOnClickListener{
            val message = binding.inputMessage.text.toString()
            if(message.isNotEmpty()){
                ChatServer.sendMessage(message)
                binding.inputMessage.setText("")
            }
        }
    }

    private fun showDisconnected(){
        hideKeyboard()
        Log.d(TAG, "ShowDisconnected: ")
       // binding.buttonSend.isEnabled=false
    }

    private fun hideKeyboard(){
        inputMethodManager.hideSoftInputFromWindow(binding.root.windowToken,0)
    }
}
package com.example.bluetalk.ui

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.bluetalk.R
import com.example.bluetalk.adapter.ChatListAdapter
import com.example.bluetalk.adapter.OnConversationSelectClickListener
import com.example.bluetalk.bluetooth.ChatServer
import com.example.bluetalk.model.Conversation
import com.example.bluetalk.model.Message
import com.example.bluetalk.model.MessageType
import com.example.bluetalk.model.User
import java.util.*

class ConversationListFragment : Fragment(), OnConversationSelectClickListener {

    private lateinit var chatListAdapter: ChatListAdapter
    private lateinit var recyclerView: RecyclerView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_conversation_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.chatListRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(context)

        // Initialize your adapter and set it to the RecyclerView

        chatListAdapter = ChatListAdapter(requireContext(), this)
        recyclerView.adapter = chatListAdapter
    }

    private fun getRemoteDevice(address: String): BluetoothDevice? {
        val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
        return bluetoothAdapter?.getRemoteDevice(address)
    }

    override fun onConversationClick(conversation: Conversation) {
        getRemoteDevice(conversation.participant.id)?.let { ChatServer.setCurrentChatConnection(it) }
        findNavController().popBackStack()
    }
}

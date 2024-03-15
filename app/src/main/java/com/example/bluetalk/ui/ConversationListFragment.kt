package com.example.bluetalk.ui

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.bluetalk.adapter.ChatListAdapter
import com.example.bluetalk.adapter.OnConversationSelectClickListener
import com.example.bluetalk.bluetooth.BluetalkServer
import com.example.bluetalk.database.ChatDao
import com.example.bluetalk.database.ChatDatabase
import com.example.bluetalk.databinding.FragmentConversationListBinding
import com.example.bluetalk.model.Conversation
import com.example.bluetalk.model.DeviceInfo
import com.example.bluetalk.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nordicsemi.android.ble.ktx.state.ConnectionState
import timber.log.Timber


private const val TAG = "ConversationListFragment"
private const val MaxHop = 10

class ConversationListFragment : Fragment(), OnConversationSelectClickListener {

    private var requestedUser: DeviceInfo?=null
    private var _binding: FragmentConversationListBinding?=null
    private val binding: FragmentConversationListBinding
        get() = _binding!!

    private lateinit var chatListAdapter: ChatListAdapter
    private var database: ChatDatabase? = null
    private var chatDao: ChatDao? = null


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
        _binding = FragmentConversationListBinding.inflate(inflater, container, false)
        chatListAdapter = ChatListAdapter(requireContext(), this)
        binding.chatListRecyclerView.layoutManager = LinearLayoutManager(context)
        binding.chatListRecyclerView.adapter = chatListAdapter
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.connectionProgress.visibility = View.GONE
        fetchConversations()
    }


    private fun proceed(){
        binding.connectionProgress.visibility = View.GONE
        val action =
            ConversationListFragmentDirections.actionConversationListFragmentToChatFragment(
                requestedUser!!.device.address,requestedUser!!.id, requestedUser!!.username
            )
        findNavController().navigate(action)
    }

    private fun fetchConversations() {
        chatDao?.getConversations()?.observe(viewLifecycleOwner) { conversations ->
            chatListAdapter.submitList(conversations)
            if(chatListAdapter.itemCount == 0){
                binding.tvNoChats.visibility = View.VISIBLE
            }else{
                binding.tvNoChats.visibility = View.GONE
            }
        }
    }

    override fun onConversationClick(conversation: Conversation) {
         binding.connectionProgress.visibility = View.VISIBLE
        val bAdapter =
            (requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        val d = bAdapter.getRemoteDevice(conversation.address)

        Timber.tag(TAG).d("${conversation.uuid}  ${conversation.username}")
        requestedUser = DeviceInfo(d, conversation.username, conversation.uuid)
        lifecycleScope.launch(Dispatchers.IO) {
            val user = chatDao?.getUserByUUID(conversation.uuid)
            if (user != null) {
                Timber.tag(TAG).d("Requested: %s", user.address)
            }

            BluetalkServer.connectUser(d)
            binding.connectionProgress.visibility = View.VISIBLE
            delay(1000)
            lifecycleScope.launch(Dispatchers.Main) {
                proceed()
            }
        }
    }

}
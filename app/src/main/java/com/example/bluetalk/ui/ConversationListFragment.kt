package com.example.bluetalk.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.bluetalk.R
import com.example.bluetalk.adapter.ChatListAdapter
import com.example.bluetalk.model.Conversation
import com.example.bluetalk.model.Message
import com.example.bluetalk.model.MessageType
import com.example.bluetalk.model.User
import java.util.*

class ConversationListFragmentFragment : Fragment() {

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
        val conversations = listOf(
            Conversation(
                "12", User("122", "Hardy", ""),
                Message(UUID.randomUUID(), "Hello from user1!", Date(), MessageType.SENT)
            ),
            Conversation(
                "13", User("122", "Motto", ""),
                Message(UUID.randomUUID(),  "Hello from user1!", Date(), MessageType.SENT)
            ),
            Conversation(
                "14", User("122", "Mukruu", ""),
                Message(UUID.randomUUID(),  "Hello from user1!", Date(), MessageType.SENT)
            )
        )
        chatListAdapter = ChatListAdapter(requireContext(), conversations)
        recyclerView.adapter = chatListAdapter
    }
}

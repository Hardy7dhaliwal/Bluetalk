package com.example.bluetalk.adapter
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.bluetalk.R
import com.example.bluetalk.model.Conversation
import com.example.bluetalk.model.Message

class ChatListAdapter (private val context: Context,private val clickListener: OnConversationSelectClickListener)
    :RecyclerView.Adapter<ChatListAdapter.ConversationHolder>(){

    private var conversationList = listOf<Conversation>()



    class ConversationHolder(private val view:View): RecyclerView.ViewHolder(view){
        private val username: TextView = view.findViewById(R.id.userNameTextView)
        private val lastMessageTimestamp: TextView = view.findViewById(R.id.lastMessageTimeTextView)
        private val  lastMessage: TextView = view.findViewById(R.id.lastMessageTextView)
        fun bind(conversation: Conversation, clickListener: OnConversationSelectClickListener){
            username.text = conversation.username
            lastMessageTimestamp.text = conversation.getTime()
            lastMessage.text = conversation.content
            view.setOnClickListener{
                clickListener.onConversationClick(conversation)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConversationHolder {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.conversation_item, parent, false)
        return ConversationHolder(view)
    }

    override fun onBindViewHolder(holder: ConversationHolder, position: Int) {
        holder.bind(conversationList[position], clickListener)
    }

    override fun getItemCount(): Int {
        return conversationList.size
    }


    @SuppressLint("NotifyDataSetChanged")
    fun submitList(result: List<Conversation>){
        conversationList = result
        notifyDataSetChanged()
    }

}

interface OnConversationSelectClickListener {
    fun onConversationClick(conversation: Conversation)
}
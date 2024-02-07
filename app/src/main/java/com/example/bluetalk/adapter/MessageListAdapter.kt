package com.example.bluetalk.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.bluetalk.R
import com.example.bluetalk.model.Message
import com.example.bluetalk.model.MessageType

private const val TAG = "MessageAdapter"
class MessageListAdapter (private val context: Context )
    : RecyclerView.Adapter<RecyclerView.ViewHolder>()
{

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
    }

    private var messageList = mutableListOf<Message>()

    class SentMessageViewHolder(private val view:View): RecyclerView.ViewHolder(view){
        private val textView: TextView = view.findViewById(R.id.sent_message_text)
        private val timeView: TextView = view.findViewById(R.id.sent_message_time)

        fun bind(message: Message){
            textView.text = message.content
            timeView.text = message.getTime()
        }
    }

    class ReceivedMessageViewHolder(private val view:View): RecyclerView.ViewHolder(view){
        private val textView: TextView = view.findViewById(R.id.received_message_text)
        private val timeView: TextView = view.findViewById(R.id.received_message_time)

        fun bind(message: Message){
            textView.text = message.content
            timeView.text = message.getTime()
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (messageList[position].messageType) {
            MessageType.SENT -> VIEW_TYPE_SENT
            MessageType.RECEIVED -> VIEW_TYPE_RECEIVED
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val view = when(viewType){
            VIEW_TYPE_SENT -> LayoutInflater.from(context).inflate(R.layout.item_message_sent, parent, false)
            VIEW_TYPE_RECEIVED -> LayoutInflater.from(context).inflate(R.layout.item_message_received, parent, false)
            else -> throw java.lang.IllegalArgumentException("Invalid View Type")
        }
        return if(viewType == VIEW_TYPE_SENT) SentMessageViewHolder(view)
                else ReceivedMessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messageList[position]
        when(holder){
            is SentMessageViewHolder -> holder.bind(message)
            is ReceivedMessageViewHolder -> holder.bind(message)
        }
    }

    override fun getItemCount() = messageList.size

    @SuppressLint("NotifyDataSetChanged")
    fun addMessage(message: Message){
        Log.d(TAG, "addMessage: ")
        messageList.add(0,message)
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun submitList(result: List<Message>){
        messageList = result as MutableList<Message>
        notifyDataSetChanged()
    }
}
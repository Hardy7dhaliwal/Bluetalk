package com.example.bluetalk.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.bluetalk.R
import com.example.bluetalk.model.User

class UserListAdapter(private val context: Context, private val userList: List<User>)
    :RecyclerView.Adapter<UserListAdapter.UserHolder>(){

    class UserHolder(private val view:View): RecyclerView.ViewHolder(view) {
        private val deviceName: TextView = view.findViewById(R.id.textViewDeviceName)
        private val deviceAddress: TextView = view.findViewById(R.id.textViewDeviceAddress)

        fun bind(user: User){
            deviceName.text = user.username
            deviceAddress.text = user.address
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserHolder {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.item_user, parent, false)
        return UserHolder(view)
    }

    override fun onBindViewHolder(holder: UserHolder, position: Int) {
        holder.bind(userList[position])
    }

    override fun getItemCount(): Int {
        return userList.size
    }
}
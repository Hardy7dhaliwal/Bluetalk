package com.example.bluetalk.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.bluetalk.R
import com.example.bluetalk.model.DeviceInfo

class UserScanListAdapter(private val context: Context,
                      private val clickListener: OnDeviceSelectClickListener)
    :RecyclerView.Adapter<UserScanListAdapter.UserHolder>(){

    private var deviceList = mutableListOf<DeviceInfo>()
    class UserHolder(private val view:View): RecyclerView.ViewHolder(view) {
        private val deviceName: TextView = view.findViewById(R.id.textViewDeviceName)
        private val deviceAddress: TextView = view.findViewById(R.id.textViewDeviceAddress)

        @SuppressLint("MissingPermission")
        fun bind(device: DeviceInfo, clickListener: OnDeviceSelectClickListener) {
            deviceName.text = device.username
            deviceAddress.text = device.device.address
            view.setOnClickListener {
                clickListener.onDeviceClick(device)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserHolder {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.item_user, parent, false)
        return UserHolder(view)
    }

    override fun onBindViewHolder(holder: UserHolder, position: Int) {
        holder.bind(deviceList[position], clickListener)
    }

    override fun getItemCount(): Int {
        return deviceList.size
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateItems(result: List<DeviceInfo>){
        deviceList = result.toMutableList()
        notifyDataSetChanged()
    }

    fun add(device: DeviceInfo) {
        // Check if the list already contains a device with the same address
        val existingDeviceIndex = deviceList.indexOfFirst { it.device.address == device.device.address }
        if (existingDeviceIndex == -1) {
            // Device not found in the list, add it
            deviceList.add(device)
            notifyItemInserted(deviceList.size - 1) // Notify only the inserted item for better performance
        } else {
            // Device already exists, update the existing device info if needed
            deviceList[existingDeviceIndex] = device
            notifyItemChanged(existingDeviceIndex) // Notify only the changed item
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun clear(){
        deviceList = mutableListOf()
        notifyDataSetChanged()
    }
}

interface OnDeviceSelectClickListener {
    fun onDeviceClick(device: DeviceInfo)
}

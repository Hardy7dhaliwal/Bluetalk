package com.example.bluetalk.adapter

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.bluetalk.R

class UserScanListAdapter(private val context: Context,
                      private val clickListener: OnDeviceClickListener)
    :RecyclerView.Adapter<UserScanListAdapter.UserHolder>(){

    private var deviceList = listOf<BluetoothDevice>()

    class UserHolder(private val view:View): RecyclerView.ViewHolder(view) {
        private val deviceName: TextView = view.findViewById(R.id.textViewDeviceName)
        private val deviceAddress: TextView = view.findViewById(R.id.textViewDeviceAddress)

        @SuppressLint("MissingPermission")
        fun bind(device: BluetoothDevice, clickListener: OnDeviceClickListener) {
            deviceName.text = device.name ?: "Unknown Device"
            deviceAddress.text = device.address ?: "00:00:00:00:00"
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
    fun updateItems(result: List<BluetoothDevice>){
        deviceList = result
        notifyDataSetChanged()
    }
}

interface OnDeviceClickListener {
    fun onDeviceClick(device: BluetoothDevice)
}

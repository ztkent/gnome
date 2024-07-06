package com.ztkent.sunlightmeter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView

class AvailableDevicesListAdapter(
    private var viewModel: MainViewModel,
    private var availableDevices: List<String>
) : RecyclerView.Adapter<AvailableDevicesListAdapter.ButtonViewHolder>() {
    class ButtonViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val button: Button = itemView.findViewById(R.id.buttonItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ButtonViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_button, parent, false) // Still using text_item.xml
        return ButtonViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ButtonViewHolder, position: Int) {
        holder.button.text = availableDevices[position]
        holder.button.setOnClickListener {
            if (connectDevice(availableDevices[position])) {
                Toast.makeText(
                    holder.itemView.context,
                    "${availableDevices[position]} connected",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    holder.itemView.context,
                    "Failed to connect ${availableDevices[position]}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun getItemCount(): Int = availableDevices.size

    // Show any found devices we arent actively connected
    fun updateAvailableDevices(currentDevices: List<String>) {
        val currentAvailableDevices = mutableListOf<String>()
        for (device in currentDevices) {
            if (!viewModel.connectedDevices.contains(device)) {
                currentAvailableDevices += device
            }
        }
        availableDevices = currentAvailableDevices
        notifyDataSetChanged()
    }

    // Connect a device if we arent already connected
    fun connectDevice(device: String): Boolean {
        if (!viewModel.connectedDevices.contains(device)) {
            viewModel.connectedDevices.add(device)
            return true
        }
        return false
    }
}
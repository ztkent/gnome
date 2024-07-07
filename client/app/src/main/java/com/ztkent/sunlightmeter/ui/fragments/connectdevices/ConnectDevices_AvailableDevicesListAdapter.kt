package com.ztkent.sunlightmeter.ui.fragments.connectdevices

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.ztkent.sunlightmeter.R
import com.ztkent.sunlightmeter.data.SunlightModel

class AvailableDevicesListAdapter(
    private var viewModel: SunlightModel,
    private var availableDevices: MutableList<String>
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
            val deviceName: String = holder.button.text as String
            if (connectDevice(deviceName)) {
                Toast.makeText(
                    holder.itemView.context,
                    "$deviceName connected",
                    Toast.LENGTH_SHORT
                ).show()

                // Remove the device for the available list, and update the view when connected
                availableDevices.remove(deviceName)
                notifyItemRemoved(position)
            } else {
                Toast.makeText(
                    holder.itemView.context,
                    "Failed to connect $deviceName",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun getItemCount(): Int = availableDevices.size

    // Connect a device if we arent already connected
    private fun connectDevice(device: String): Boolean {
        if (!viewModel.connectedDevices.contains(device)) {
            viewModel.connectedDevices.add(device)
            return true
        }
        return false
    }

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

}
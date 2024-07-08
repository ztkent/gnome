package com.ztkent.sunlightmeter.ui.fragments.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ztkent.sunlightmeter.R
import com.ztkent.sunlightmeter.data.SunlightModel

class ConnectedDevicesListAdapter(
    private var viewModel: SunlightModel,
    private val lifecycleOwner: LifecycleOwner
) :
    RecyclerView.Adapter<ConnectedDevicesListAdapter.ButtonViewHolder>() {


    init {
        viewModel.connectedDevices.observe(lifecycleOwner) { newDevices ->
            notifyDataSetChanged() // Update RecyclerView when LiveData changes
        }
    }

    class ButtonViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val button: Button = itemView.findViewById(R.id.buttonItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ButtonViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_button, parent, false)
        return ButtonViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ButtonViewHolder, position: Int) {
        val devices = viewModel.connectedDevices.value ?: emptyList()
        holder.button.text = devices[position]
        holder.button.setOnClickListener {
            Toast.makeText(
                holder.itemView.context,
                "Button ${position + 1} clicked",
                Toast.LENGTH_SHORT
            ).show()
        }

        holder.button.setOnLongClickListener {
            MaterialAlertDialogBuilder(holder.itemView.context)
                .setTitle("Remove Connected Device")
                .setMessage("Disconnect sunlight meter device?")
                .setNegativeButton("Cancel", null) // Do nothing on cancel
                .setPositiveButton("Delete") { _, _ ->
                    // Delete the device
                    val currentList =
                        viewModel.connectedDevices.value?.toMutableList() ?: mutableListOf()
                    currentList.remove(holder.button.text)
                    viewModel.updateConnectedDevices(currentList)
                }
                .show()
            true
        }
    }

    override fun getItemCount(): Int {
        return viewModel.connectedDevices.value?.size ?: 0
    }
}

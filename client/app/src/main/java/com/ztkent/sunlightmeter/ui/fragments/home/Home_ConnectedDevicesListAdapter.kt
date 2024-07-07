package com.ztkent.sunlightmeter.ui.fragments.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ztkent.sunlightmeter.R
import com.ztkent.sunlightmeter.data.SunlightModel

class ConnectedDevicesListAdapter(
    private var viewModel: SunlightModel
) :
    RecyclerView.Adapter<ConnectedDevicesListAdapter.ButtonViewHolder>() {

    class ButtonViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val button: Button = itemView.findViewById(R.id.buttonItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ButtonViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_button, parent, false)
        return ButtonViewHolder(itemView)
    }
    
    override fun onBindViewHolder(holder: ButtonViewHolder, position: Int) {
        holder.button.text = viewModel.connectedDevices[position]
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
                    viewModel.connectedDevices.remove(holder.button.text)
                    notifyItemRemoved(position)
                }
                .show()
            true
        }
    }

    override fun getItemCount(): Int = viewModel.connectedDevices.size
}

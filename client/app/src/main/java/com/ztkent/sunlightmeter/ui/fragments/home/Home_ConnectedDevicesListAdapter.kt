package com.ztkent.sunlightmeter.ui.fragments.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.ztkent.sunlightmeter.R

class ConnectedDevicesListAdapter(private val textList: List<String>) :
    RecyclerView.Adapter<ConnectedDevicesListAdapter.ButtonViewHolder>() {

    class ButtonViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val button: Button = itemView.findViewById(R.id.buttonItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ButtonViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_button, parent, false)
        return ButtonViewHolder(itemView)
    }

    // TODO: Long click to remove connected devices?

    override fun onBindViewHolder(holder: ButtonViewHolder, position: Int) {
        holder.button.text = textList[position]
        holder.button.setOnClickListener {
            Toast.makeText(
                holder.itemView.context,
                "Button ${position + 1} clicked",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun getItemCount(): Int = textList.size
}

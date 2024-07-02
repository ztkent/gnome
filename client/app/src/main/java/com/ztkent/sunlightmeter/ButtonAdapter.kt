package com.ztkent.sunlightmeter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView

class ButtonAdapter(private val textList: List<String>) :
    RecyclerView.Adapter<ButtonAdapter.ButtonViewHolder>() {

    class ButtonViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val button: Button = itemView.findViewById(R.id.buttonItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ButtonViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_button, parent, false) // Still using text_item.xml
        return ButtonViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ButtonViewHolder, position: Int) {
        holder.button.text = textList[position]
        // You can add button click listeners here if needed:
        holder.button.setOnClickListener {
            // Handle button click for item at position
            // For example:
            Toast.makeText(holder.itemView.context, "Button ${position + 1} clicked", Toast.LENGTH_SHORT).show()
        }
    }

    override fun getItemCount(): Int = textList.size
}

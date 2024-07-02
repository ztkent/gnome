package com.ztkent.sunlightmeter

import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Attach activity handlers
        setupHandlers()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setupHandlers() {
        val textDataList = mutableListOf("Test String 1", "Test String 2", "Test String 3")
        val linkDeviceButton: Button = findViewById(R.id.linkDeviceButton)
        val recyclerView: RecyclerView = findViewById(R.id.buttonRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = ButtonAdapter(textDataList)

        linkDeviceButton.setOnClickListener {
            recyclerView.adapter = DeviceListAdapter()
        }
    }
}

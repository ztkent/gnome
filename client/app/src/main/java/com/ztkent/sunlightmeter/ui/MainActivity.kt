package com.ztkent.sunlightmeter.ui

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.ztkent.sunlightmeter.R
import com.ztkent.sunlightmeter.data.SunlightModel
import com.ztkent.sunlightmeter.data.repository.Repository
import com.ztkent.sunlightmeter.ui.fragments.home.HomeFragment

// A container for app fragments
class MainActivity : AppCompatActivity() {
    private val repository by lazy { Repository(application) } // or Repository(application)
    private val viewModel: SunlightModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Inflate the container
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Set repo to the ViewModel
        viewModel.repository = repository

        val fragmentManager = supportFragmentManager
        val fragmentTransaction = fragmentManager.beginTransaction()
        val homeFragment = HomeFragment()
        fragmentTransaction.add(R.id.fragment_container, homeFragment)
        fragmentTransaction.commit()

        // Initialize the list of connected devices from DB
        viewModel.fetchStoredConnectedDevices()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }


    // Handle cleanup events
    override fun onPause() {
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
    }


}
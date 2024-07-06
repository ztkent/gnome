package com.ztkent.sunlightmeter

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.ztkent.sunlightmeter.data.SunlightModel
import com.ztkent.sunlightmeter.fragments.home.HomeFragment

// A container for app fragments
class MainActivity : AppCompatActivity() {
    private val viewModel: SunlightModel by viewModels() // Initialize the shared ViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        // Inflate the container
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)


        val fragmentManager = supportFragmentManager
        val fragmentTransaction = fragmentManager.beginTransaction()
        val homeFragment = HomeFragment()
        fragmentTransaction.add(R.id.fragment_container, homeFragment)
        fragmentTransaction.commit()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }
}
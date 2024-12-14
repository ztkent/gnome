package com.ztkent.sunlightmeter

import android.os.Handler
import android.os.Looper
import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.ztkent.sunlightmeter.ui.theme.SunlightMeterTheme

class LandingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
            // Display splash screen and set first_launch to false
            enableEdgeToEdge()
            setContent {
                SunlightMeterTheme {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        ImageSplashScreen(
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            Handler(Looper.getMainLooper()).postDelayed({
                val intent = Intent(this, SunlightActivity::class.java)
                startActivity(intent)
                finish()
            }, 2000)
        }
    }
}
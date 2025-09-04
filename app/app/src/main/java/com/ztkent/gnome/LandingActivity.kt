package com.ztkent.gnome

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import com.ztkent.gnome.ui.theme.SunlightMeterTheme

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
            Handler(Looper.getMainLooper()).post({
                val intent = Intent(this, GnomeActivity::class.java)
                startActivity(intent)
                finish()
            })
        }
    }
}

@Composable
fun ImageSplashScreen(modifier: Modifier = Modifier) {
    val image = painterResource(id = R.drawable.splash_bg)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Image(
            painter = image,
            contentDescription = "Splash Image",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.2f)) // Semi-transparent black box
        ) {}
    }
}

@Preview(showBackground = true)
@Composable
fun ImageSplashScreenPreview() {
    ImageSplashScreen()
}

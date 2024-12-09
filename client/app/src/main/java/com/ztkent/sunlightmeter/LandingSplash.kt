package com.ztkent.sunlightmeter


import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    val gradient = Brush.linearGradient(
        colors = listOf(Color(0xFFA4D34F), Color(0xFF4CAF50)),
        start = Offset.Zero,
        end = Offset.Infinite
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(gradient)
    ) {
        Text(
            text = "Sunlight Meter",
            style = TextStyle(
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Transparent, // Set color to transparent for gradient to be visible
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.2f),
                    offset = Offset(2f, 2f),
                    blurRadius = 4f
                ),
                letterSpacing = 1.sp // Adjust letter spacing
            ),
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-80).dp)
        )

        // Sun
        Box(
            modifier = Modifier
                .size(90.dp) // Increased size by approximately 40% (64dp * 1.4 ≈ 90dp)
                .align(Alignment.TopStart)
                .padding(16.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.Yellow, Color.Transparent),
                        center = Offset(
                            with(LocalDensity.current) { 45.dp.toPx() },
                            with(LocalDensity.current) { 45.dp.toPx() }), // Adjust center for larger size
                        radius = with(LocalDensity.current) { 67.dp.toPx() } // Adjust radius for larger size
                    ),
                    CircleShape
                )
        )
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
                .background(Color.Black.copy(alpha = 0.4f)) // Semi-transparent black box
        ) {
            Text(
                text = "Sunlight Meter",
                style = TextStyle(
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFBBB5B5)
                ),
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = (-80).dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ImageSplashScreenPreview() {
    ImageSplashScreen()
}


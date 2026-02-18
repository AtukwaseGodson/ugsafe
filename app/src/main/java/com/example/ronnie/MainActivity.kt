package com.example.ronnie

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.ronnie.ui.theme.RonnieTheme

// Define the possible screens in your app
enum class Screen {
    Home,
    Camera,
    Preview
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RonnieTheme {
                val context = LocalContext.current

                // Track which screen is currently visible
                var currentScreen by remember { mutableStateOf(Screen.Home) }

                // Track the captured photo
                var capturedUri by remember { mutableStateOf<Uri?>(null) }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {

                        // Navigation Logic
                        when (currentScreen) {
                            Screen.Home -> {
                                HomeScreen(onNavigateToCamera = {
                                    currentScreen = Screen.Camera
                                })
                            }

                            Screen.Camera -> {
                                CameraScreen(onPhotoCaptured = { uri ->
                                    capturedUri = uri
                                    currentScreen = Screen.Preview
                                })
                            }

                            Screen.Preview -> {
                                capturedUri?.let { uri ->
                                    PreviewScreen(
                                        imageUri = uri,
                                        onRetake = {
                                            capturedUri = null
                                            currentScreen = Screen.Camera
                                        },
                                        onConfirm = {
                                            Toast.makeText(context, "Reported!", Toast.LENGTH_SHORT).show()
                                            // Go back to home after finishing
                                            currentScreen = Screen.Home
                                            capturedUri = null
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
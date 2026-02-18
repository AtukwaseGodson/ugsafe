package com.example.ronnie

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import java.io.File

@Composable
fun CameraScreen(
    onPhotoCaptured: (Uri) -> Unit // This callback triggers the screen swap
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val imageCapture = remember { ImageCapture.Builder().build() }

    // Permission Handling for both Camera and Location
    var hasPermissions by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermissions = permissions.values.all { it }
    }

    // Request permissions on launch
    LaunchedEffect(Unit) {
        launcher.launch(arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION
        ))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (hasPermissions) {
            CameraPreview(lifecycleOwner, imageCapture)

            // Shutter Button
            FloatingActionButton(
                onClick = {
                    capturePhotoWithLocation(context, imageCapture, onPhotoCaptured)
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(32.dp)
                    .size(70.dp),
                containerColor = Color.White
            ) {
                Icon(Icons.Default.Camera, contentDescription = "Capture", tint = Color.Black)
            }
        } else {
            Button(
                onClick = {
                    launcher.launch(arrayOf(
                        Manifest.permission.CAMERA,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ))
                },
                modifier = Modifier.align(Alignment.Center)
            ) {
                Text("Grant Permissions")
            }
        }
    }
}

@Composable
fun CameraPreview(
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    imageCapture: ImageCapture
) {
    val context = LocalContext.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        modifier = Modifier.fillMaxSize(),
        update = { previewView ->
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageCapture
                    )
                } catch (e: Exception) {
                    Log.e("CameraScreen", "Binding failed", e)
                }
            }, ContextCompat.getMainExecutor(context))
        }
    )
}

@SuppressLint("MissingPermission")
private fun capturePhotoWithLocation(
    context: Context,
    imageCapture: ImageCapture,
    onSuccess: (Uri) -> Unit
) {
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    // 1. Get location first to attach to metadata
    fusedLocationClient.lastLocation.addOnSuccessListener { location ->
        val photoFile = File(context.cacheDir, "Incident_${System.currentTimeMillis()}.jpg")

        val metadata = ImageCapture.Metadata().apply {
            this.location = location // Coordinates attached here!
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile)
            .setMetadata(metadata)
            .build()

        // 2. Take photo
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    // 3. This triggers the automatic transition in MainActivity
                    onSuccess(Uri.fromFile(photoFile))
                }

                override fun onError(exc: ImageCaptureException) {
                    Log.e("Camera", "Capture failed", exc)
                }
            }
        )
    }
}
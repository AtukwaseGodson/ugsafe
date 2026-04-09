package com.example.ugsafe

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ugsafe.ui.theme.UgsafeTheme
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.classifier.ImageClassifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            UgsafeTheme {
                val context = LocalContext.current
                var classificationResult by remember { mutableStateOf("Ready to scan") }
                var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
                var isEmergency by remember { mutableStateOf(false) }

                val cameraLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.TakePicturePreview()
                ) { bitmap ->
                    if (bitmap != null) {
                        capturedBitmap = bitmap
                        val result = runInference(bitmap)
                        classificationResult = result
                        // Change UI color if emergency is detected
                        isEmergency = result.contains("Emergency", ignoreCase = true)
                    }
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    if (isGranted) cameraLauncher.launch()
                    else Toast.makeText(context, "Permission denied", Toast.LENGTH_SHORT).show()
                }

                Scaffold(
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = { Text("UGSAFE DETECTOR", fontWeight = FontWeight.Bold) },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Top
                    ) {
                        // Image Preview Card
                        ElevatedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                if (capturedBitmap != null) {
                                    Image(
                                        bitmap = capturedBitmap!!.asImageBitmap(),
                                        contentDescription = "Captured Image",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Text("No image captured", color = Color.Gray)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        // Status Result Card
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = if (isEmergency) Color(0xFFFFDAD6) else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("ANALYSIS RESULT", style = MaterialTheme.typography.labelLarge)
                                Text(
                                    text = classificationResult,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isEmergency) Color.Red else Color.Unspecified
                                )
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // Action Button
                        Button(
                            onClick = {
                                val permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                                if (permissionCheck == PackageManager.PERMISSION_GRANTED) cameraLauncher.launch()
                                else permissionLauncher.launch(Manifest.permission.CAMERA)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("SCAN ENVIRONMENT", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }

    private fun runInference(bitmap: Bitmap): String {
        return try {
            val options = ImageClassifier.ImageClassifierOptions.builder()
                .setMaxResults(1)
                .setScoreThreshold(0.5f) // Built-in thresholding
                .build()

            val classifier = ImageClassifier.createFromFileAndOptions(this, "emergency_detector.tflite", options)
            val image = TensorImage.fromBitmap(bitmap)
            val results = classifier.classify(image)

            val topCategory = results.firstOrNull()?.categories?.firstOrNull()

            if (topCategory != null) {
                // Double check score logic
                if (topCategory.score >= 0.50f) {
                    "${topCategory.label} (${(topCategory.score * 100).toInt()}%)"
                } else {
                    "Uncertain: Confidence too low"
                }
            } else {
                "Low Confidence / No Match"
            }
        } catch (e: Exception) {
            "Error: ${e.localizedMessage}"
        }
    }
}
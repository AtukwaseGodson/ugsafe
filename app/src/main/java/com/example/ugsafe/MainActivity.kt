package com.example.ugsafe

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.example.ugsafe.ui.theme.UgsafeTheme
import com.google.android.gms.location.LocationServices
import org.json.JSONObject
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.classifier.ImageClassifier
import java.io.ByteArrayOutputStream

class MainActivity : ComponentActivity() {

    // EMAILJS CONFIGURATION - Repla
    private val SERVICE_ID = "service_rl6q479"
    private val TEMPLATE_ID = "template_ltb801s"
    private val PUBLIC_KEY = "vWmYa4lzK2EmOe2FM"

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UgsafeTheme {
                val context = LocalContext.current
                var classificationResult by remember { mutableStateOf("Ready to scan the environment.") }
                var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
                var isEmergency by remember { mutableStateOf(false) }

                val cameraLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.TakePicturePreview()
                ) { bitmap ->
                    if (bitmap != null) {
                        capturedBitmap = bitmap
                        val rawOutput = runInference(bitmap)
                        val label = rawOutput.split(" ").first()
                        val score = (rawOutput.substringAfter("(", "0").substringBefore("%").toFloatOrNull() ?: 0f) / 100f

                        classificationResult = getDescriptiveResponse(label, score)
                        isEmergency = (label.contains("Fire", true) || label.contains("Accident", true)) && score >= 0.5f

                        if (isEmergency) {
                            sendAnonymousReport(context, label, bitmap)
                        }
                    }
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    if (permissions[Manifest.permission.CAMERA] == true) cameraLauncher.launch()
                }

                Scaffold(
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = { Text("INCIDENT DETECTOR AND REPORTER", fontWeight = FontWeight.ExtraBold) },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        )
                    }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier.padding(innerPadding).fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ElevatedCard(modifier = Modifier.fillMaxWidth().height(280.dp), shape = RoundedCornerShape(20.dp)) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                capturedBitmap?.let { Image(bitmap = it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                                    ?: Text("Capture image to start", color = Color.Gray)
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        ElevatedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = Color.Black)) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text(if (isEmergency) "⚠️ INCIDENT DETECTED" else "✅ STATUS", fontWeight = FontWeight.Bold, color = if (isEmergency) Color.Red else Color(0xFF388E3C))
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(text = classificationResult, style = MaterialTheme.typography.bodyLarge)
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        Button(
                            onClick = {
                                val permissions = arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
                                if (permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) {
                                    cameraLauncher.launch()
                                } else {
                                    permissionLauncher.launch(permissions)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("SCAN ENVIRONMENT", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission") // This removes the red underline
    private fun sendAnonymousReport(context: android.content.Context, incidentType: String, bitmap: Bitmap) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

        // Check for both Fine and Coarse location
        val hasFineLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarseLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (hasFineLocation || hasCoarseLocation) {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location: android.location.Location? ->
                    // Use a proper Google Maps URL format
                    val locLink = if (location != null) {
                        "https://www.google.com/maps/search/?api=1&query=${location.latitude},${location.longitude}"
                    } else {
                        "Location Unavailable (GPS might be off)"
                    }
                    postToEmailJS(context, incidentType, bitmap, locLink)
                }
                .addOnFailureListener {
                    postToEmailJS(context, incidentType, bitmap, "Location Error: ${it.message}")
                }
        } else {
            postToEmailJS(context, incidentType, bitmap, "Permission Denied by User")
        }
    }

    private fun postToEmailJS(context: android.content.Context, type: String, bitmap: Bitmap, mapsLink: String) {
        // 1. Scale down the image dimensions (e.g., max 400px width)
        val scaledBitmap = if (bitmap.width > 400) {
            val ratio = 400f / bitmap.width
            Bitmap.createScaledBitmap(bitmap, 400, (bitmap.height * ratio).toInt(), true)
        } else {
            bitmap
        }

        val stream = ByteArrayOutputStream()
        // 2. Use a much lower quality (20) to ensure the Base64 string isn't massive
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 20, stream)
        val base64Image = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)

        val json = JSONObject().apply {
            put("service_id", SERVICE_ID)
            put("template_id", TEMPLATE_ID)
            put("user_id", PUBLIC_KEY)
            put("template_params", JSONObject().apply {
                put("incident_type", type)
                put("location_link", mapsLink)
                put("device_info", "${Build.MANUFACTURER} ${Build.MODEL}")
                put("image_data", "data:image/jpeg;base64,$base64Image")
            })
        }

        val queue = Volley.newRequestQueue(context)
        val request = object : JsonObjectRequest(Method.POST, "https://api.emailjs.com/api/v1.0/email/send", json,
            { response ->
                Log.d("UGSAFE", "EmailJS Success: $response")
                Toast.makeText(context, "Authorities Notified Successfully", Toast.LENGTH_LONG).show()
            },
            { error ->
                val statusCode = error.networkResponse?.statusCode
                val errorMessage = error.networkResponse?.data?.let { String(it) } ?: error.message
                Log.e("UGSAFE", "Status Code: $statusCode | Error: $errorMessage")
                Toast.makeText(context, "Network Error ($statusCode): Reporting failed", Toast.LENGTH_SHORT).show()
            }
        ) {
            override fun getHeaders(): MutableMap<String, String> {
                val headers = HashMap<String, String>()
                headers["Content-Type"] = "application/json"
                headers["Origin"] = "http://localhost" // Keep this for security
                return headers
            }
        }

        request.retryPolicy = com.android.volley.DefaultRetryPolicy(
            30000, // 30 seconds timeout
            0,     // No retries (to avoid sending multiple emails)
            com.android.volley.DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        )

        queue.add(request)
    }

    private fun getDescriptiveResponse(label: String, score: Float): String {
        return when {
            score < 0.5f -> "Analyzing... low confidence. Please retake the photo in better light."
            label.contains("Fire", true) -> "CRITICAL: Fire detected. Emergency teams are being notified at atahoronnie and atukwasegodson."
            label.contains("Accident", true) -> "URGENT: Accident detected. Dispatching campus safety report."
            else -> "Environment scanned. No hazards detected."
        }
    }

    private fun runInference(bitmap: Bitmap): String {
        return try {
            val classifier = ImageClassifier.createFromFileAndOptions(this, "emergency_detector.tflite", ImageClassifier.ImageClassifierOptions.builder().setMaxResults(1).setScoreThreshold(0.5f).build())
            val results = classifier.classify(TensorImage.fromBitmap(bitmap))
            val top = results.firstOrNull()?.categories?.firstOrNull()
            if (top != null) "${top.label} (${(top.score * 100).toInt()}%)" else "Normal (0%)"
        } catch (e: Exception) { "Error (0%)" }
    }
}
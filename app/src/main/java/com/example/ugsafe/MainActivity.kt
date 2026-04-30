package com.example.ugsafe

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.example.ugsafe.ui.theme.*
import com.google.android.gms.location.LocationServices
import org.json.JSONObject
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.classifier.ImageClassifier
import java.io.ByteArrayOutputStream

class MainActivity : ComponentActivity() {

    private val SERVICE_ID = "service_rl6q479"
    private val TEMPLATE_ID = "template_ltb801s"
    private val PUBLIC_KEY = "vWmYa4lzK2EmOe2FM"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UgsafeTheme {
                MainScreen()
            }
        }
    }

    @Composable
    fun MainScreen() {
        val context = LocalContext.current
        var classificationResult by remember { mutableStateOf("Ready to scan") }
        var classificationDetail by remember { mutableStateOf("AI system is active and ready.") }
        var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
        var isEmergency by remember { mutableStateOf(false) }
        var locationStatus by remember { mutableStateOf("Fetching location...") }

        LaunchedEffect(Unit) {
            checkLocationStatus(context) { status -> locationStatus = status }
        }

        val cameraLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.TakePicturePreview()
        ) { bitmap ->
            if (bitmap != null) {
                capturedBitmap = bitmap
                val rawOutput = runInference(bitmap)
                val label = rawOutput.split(" ").first()
                val score = (rawOutput.substringAfter("(", "0").substringBefore("%").toFloatOrNull() ?: 0f) / 100f

                classificationResult = if (label.contains("Fire", true) || label.contains("Accident", true)) "Incident Detected" else "Clear Environment"
                classificationDetail = getDescriptiveResponse(label, score)
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

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = DarkBg
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Using the provided App Icon
                        Image(
                            painter = painterResource(id = R.drawable.ug_safe_app_icon),
                            contentDescription = "UgSafe Logo",
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Fit
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row {
                                Text("Ug", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                                Text("Safe", color = AccentRed, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                            }
                            Text("AI-Powered Emergency Detection", color = TextGray, fontSize = 11.sp)
                        }
                    }
                    IconButton(
                        onClick = { /* Notifications */ },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(CardBg)
                            .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape)
                    ) {
                        Icon(Icons.Outlined.Notifications, contentDescription = "Notifications", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Scanning Area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(CardBg)
                        .clickable {
                            val permissions = arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
                            if (permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) {
                                cameraLauncher.launch()
                            } else {
                                permissionLauncher.launch(permissions)
                            }
                        }
                ) {
                    capturedBitmap?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Box(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                            CornerMarker(Alignment.TopStart)
                            CornerMarker(Alignment.TopEnd)
                            CornerMarker(Alignment.BottomStart)
                            CornerMarker(Alignment.BottomEnd)
                        }
                        
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = AccentRed, modifier = Modifier.size(56.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Tap to scan environment", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("Point your camera to detect incidents", color = TextGray, fontSize = 13.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Status Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg)
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(if (isEmergency) AccentRed.copy(alpha = 0.15f) else StatusGreen.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (isEmergency) Icons.Default.Warning else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (isEmergency) AccentRed else StatusGreen,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "STATUS",
                                color = if (isEmergency) AccentRed else StatusGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(classificationResult, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            Text(classificationDetail, color = TextGray, fontSize = 13.sp)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Psychology, contentDescription = null, tint = Color(0xFFB39DDB), modifier = Modifier.size(32.dp))
                            Text("AI Active", color = Color(0xFFB39DDB), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Scan Button
                Button(
                    onClick = {
                        val permissions = arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
                        if (permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) {
                            cameraLauncher.launch()
                        } else {
                            permissionLauncher.launch(permissions)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(70.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(28.dp))
                        Column(horizontalAlignment = Alignment.Start, modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                            Text("SCAN ENVIRONMENT", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Detect incidents and hazards", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                Text("QUICK ACTIONS", color = TextGray, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    QuickActionCard(
                        title = "Emergency Call",
                        subtitle = "Call 999",
                        icon = Icons.Default.Phone,
                        color = AccentRed,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:999"))
                            context.startActivity(intent)
                        }
                    )

                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF7E57C2).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFF7E57C2), modifier = Modifier.size(22.dp))
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Location Status", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(locationStatus, color = TextGray, fontSize = 11.sp, lineHeight = 14.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    border = BorderStroke(1.dp, BorderGray)
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = AccentRed, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Your safety is our priority", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("In case of emergency, stay calm and let UgSafe help.", color = TextGray, fontSize = 12.sp)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextGray)
                    }
                }
            }
        }
    }

    @Composable
    fun CornerMarker(alignment: Alignment) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = alignment) {
            Box(modifier = Modifier.size(24.dp)) {
                Box(modifier = Modifier
                    .align(alignment)
                    .width(20.dp).height(3.dp).background(AccentRed, RoundedCornerShape(2.dp)))
                Box(modifier = Modifier
                    .align(alignment)
                    .width(3.dp).height(20.dp).background(AccentRed, RoundedCornerShape(2.dp)))
            }
        }
    }

    @Composable
    fun QuickActionCard(title: String, subtitle: String, icon: ImageVector, color: Color, modifier: Modifier, onClick: () -> Unit) {
        Card(
            modifier = modifier.clickable { onClick() },
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = CardBg),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(subtitle, color = TextGray, fontSize = 11.sp, lineHeight = 14.sp)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun checkLocationStatus(context: android.content.Context, onResult: (String) -> Unit) {
        val hasFineLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFineLocation) {
            onResult("Permission denied.")
            return
        }
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) onResult("${location.latitude.toString().take(6)}, ${location.longitude.toString().take(6)}")
            else onResult("Location unavailable.")
        }.addOnFailureListener { onResult("Error: ${it.message}") }
    }

    @SuppressLint("MissingPermission")
    private fun sendAnonymousReport(context: android.content.Context, incidentType: String, bitmap: Bitmap) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        val hasFineLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarseLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (hasFineLocation || hasCoarseLocation) {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location: android.location.Location? ->
                    val locLink = if (location != null) "https://www.google.com/maps/search/?api=1&query=${location.latitude},${location.longitude}"
                    else "Location Unavailable"
                    postToEmailJS(context, incidentType, bitmap, locLink)
                }
                .addOnFailureListener { postToEmailJS(context, incidentType, bitmap, "Location Error") }
        } else {
            postToEmailJS(context, incidentType, bitmap, "Permission Denied")
        }
    }

    private fun postToEmailJS(context: android.content.Context, type: String, bitmap: Bitmap, mapsLink: String) {
        val scaledBitmap = if (bitmap.width > 400) {
            val ratio = 400f / bitmap.width
            Bitmap.createScaledBitmap(bitmap, 400, (bitmap.height * ratio).toInt(), true)
        } else bitmap

        val stream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 20, stream)
        val base64Data = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)

        val json = JSONObject().apply {
            put("service_id", SERVICE_ID)
            put("template_id", TEMPLATE_ID)
            put("user_id", PUBLIC_KEY)
            put("template_params", JSONObject().apply {
                put("incident_type", type)
                put("location_link", mapsLink)
                put("device_info", "${Build.MANUFACTURER} ${Build.MODEL}")
                put("image_data", "data:image/jpeg;base64,$base64Data")
            })
        }

        val queue = Volley.newRequestQueue(context)
        val request = object : JsonObjectRequest(Method.POST, "https://api.emailjs.com/api/v1.0/email/send", json,
            { Log.d("UGSAFE", "EmailJS Success") },
            { Log.e("UGSAFE", "EmailJS Error") }
        ) {
            override fun getHeaders(): MutableMap<String, String> {
                val headers = HashMap<String, String>()
                headers["Content-Type"] = "application/json"
                headers["Origin"] = "http://localhost"
                return headers
            }
        }
        queue.add(request)
    }

    private fun getDescriptiveResponse(label: String, score: Float): String {
        return when {
            score < 0.5f -> "Analyzing... low confidence."
            label.contains("Fire", true) -> "CRITICAL: Fire detected. Emergency teams notified."
            label.contains("Accident", true) -> "URGENT: Accident detected. Dispatching report."
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

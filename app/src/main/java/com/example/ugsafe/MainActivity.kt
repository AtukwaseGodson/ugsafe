package com.example.ugsafe

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
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

    // EmailJS Credentials
    private val SERVICE_ID = "service_rl6q479"
    private val TEMPLATE_ID = "template_ltb801s"
    private val PUBLIC_KEY = "vWmYa4lzK2EmOe2FM"

    // Authority Emails
    private val FIRE_BRIGADE_EMAIL = "mucureezioliviah@gmail.com"
    private val POLICE_DEPT_EMAIL = "atukwasegodson@gmail.com"

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
        var classificationResult by remember { mutableStateOf("System Ready") }
        var classificationDetail by remember { mutableStateOf("The AI monitor is active and waiting to help in case of emergencies.") }
        var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
        var isEmergency by remember { mutableStateOf(false) }
        var locationStatus by remember { mutableStateOf("Locating device...") }
        var reportStatus by remember { mutableStateOf("") }
        var nearbyHelpUrl by remember { mutableStateOf("") }
        var nearbyHelpType by remember { mutableStateOf("") }
        var rawInference by remember { mutableStateOf("") }

        LaunchedEffect(Unit) {
            checkLocationStatus(context) { status -> locationStatus = status }
        }

        val cameraLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.TakePicturePreview()
        ) { bitmap ->
            if (bitmap != null) {
                capturedBitmap = bitmap
                val rawOutput = runInference(bitmap)
                rawInference = rawOutput
                val label = rawOutput.split(" ").first()
                val score = (rawOutput.substringAfter("(", "0").substringBefore("%").toFloatOrNull() ?: 0f) / 100f

                val isFire = label.equals("fire", ignoreCase = true)
                val isAccident = label.equals("accident", ignoreCase = true)
                
                isEmergency = (isFire || isAccident) && score >= 0.5f

                if (isEmergency) {
                    val targetDept = if (isFire) "Fire Brigade" else "Police Department"
                    val targetEmail = if (isFire) FIRE_BRIGADE_EMAIL else POLICE_DEPT_EMAIL
                    
                    classificationResult = "Emergency Detected"
                    classificationDetail = if (isFire) {
                        "Critical fire hazard identified. Alerting the Fire Brigade immediately. please stay calm as we come to the rescue"
                    } else {
                        "Road accident identified. Alerting the Police Department immediately. Try finding the nearby hospital for the injured fellows"
                    }
                    
                    reportStatus = "Routing distress signal to $targetDept..."
                    
                    // Generate Directions URL (Shortest Route)
                    generateDirectionsUrl(context, if (isFire) "fire+station" else "police+station") { url ->
                        nearbyHelpUrl = url
                        nearbyHelpType = if (isFire) "Fire Station" else "Police Station"
                    }

                    sendAnonymousReport(context, if(isFire) "Fire" else "Accident", targetDept, targetEmail, bitmap) { status ->
                        reportStatus = status
                    }
                } else {
                    classificationResult = label.replace("_", " ").uppercase()
                    classificationDetail = getDescriptiveResponse(label, score)
                    reportStatus = ""
                    nearbyHelpUrl = ""
                    nearbyHelpType = ""
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
                            Text("AI-Powered Safety Network", color = TextGray, fontSize = 11.sp)
                        }
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
                            Text("Tap to Scan Environment", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("Our AI will analyze the scene for hazards", color = TextGray, fontSize = 13.sp)
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
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(if (isEmergency) AccentRed.copy(alpha = 0.15f) else StatusGreen.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    if (isEmergency) Icons.Default.Warning else Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = if (isEmergency) AccentRed else StatusGreen,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isEmergency) "ACTION REQUIRED" else "SAFETY STATUS",
                                    color = if (isEmergency) AccentRed else StatusGreen,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(classificationResult, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                if (rawInference.isNotEmpty()) {
                                    Text("AI Detection: $rawInference", color = Color(0xFFB39DDB), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.AutoGraph, contentDescription = null, tint = Color(0xFFB39DDB), modifier = Modifier.size(32.dp))
                                Text("AI Watch", color = Color(0xFFB39DDB), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(classificationDetail, color = TextGray, fontSize = 14.sp, lineHeight = 20.sp)
                        
                        if (reportStatus.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (reportStatus.contains("delivered", true)) StatusGreen.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f))
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (reportStatus.contains("Routing", true)) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                                } else {
                                    Icon(
                                        if (reportStatus.contains("delivered", true)) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                                        contentDescription = null,
                                        tint = if (reportStatus.contains("delivered", true)) StatusGreen else AccentRed,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    reportStatus, 
                                    color = Color.White, 
                                    fontSize = 13.sp, 
                                    fontWeight = FontWeight.Medium,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }

                if (isEmergency && nearbyHelpUrl.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(nearbyHelpUrl))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("ROUTE TO NEAREST ${nearbyHelpType.uppercase()}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Open navigation for the fastest path", fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
                            }
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
                            Text("SCAN NOW", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Start AI-powered hazard detection", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                Text("QUICK ASSISTANCE", color = TextGray, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    QuickActionCard(
                        title = "Emergency Help",
                        subtitle = "Dial 999",
                        icon = Icons.Default.Call,
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
                                Icon(Icons.Default.MyLocation, contentDescription = null, tint = Color(0xFF7E57C2), modifier = Modifier.size(22.dp))
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("My Location", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(locationStatus, color = TextGray, fontSize = 11.sp, lineHeight = 14.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                Text("FIND NEARBY SERVICES", color = TextGray, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NearbyServiceCard(
                        title = "Hospital",
                        icon = Icons.Default.LocalHospital,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=hospital"))
                            context.startActivity(intent)
                        }
                    )
                    NearbyServiceCard(
                        title = "Police",
                        icon = Icons.Default.LocalPolice,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=police+station"))
                            context.startActivity(intent)
                        }
                    )
                    NearbyServiceCard(
                        title = "Fire",
                        icon = Icons.Default.FireTruck,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=fire+station"))
                            context.startActivity(intent)
                        }
                    )
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
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = StatusGreen, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Safe & Anonymous", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("Your reports are encrypted and sent securely to responsible authorities.", color = TextGray, fontSize = 12.sp)
                        }
                    }
                }

                Text(
                    text = "UgSafe AI can make mistakes. Always prioritize your safety and use professional judgment in emergencies.",
                    color = TextGray.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }

    @Composable
    fun NearbyServiceCard(title: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
        Card(
            modifier = modifier.clickable { onClick() },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardBg),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
            onResult("Permission Required")
            return
        }
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) onResult("Location Active")
            else onResult("Location Unknown")
        }.addOnFailureListener { onResult("Location Error") }
    }

    @SuppressLint("MissingPermission")
    private fun generateDirectionsUrl(context: Context, destinationType: String, onUrlGenerated: (String) -> Unit) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            val origin = if (location != null) "${location.latitude},${location.longitude}" else ""
            val url = "https://www.google.com/maps/dir/?api=1&origin=$origin&destination=$destinationType&travelmode=driving"
            onUrlGenerated(url)
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendAnonymousReport(context: android.content.Context, incidentType: String, targetDept: String, targetEmail: String, bitmap: Bitmap, onStatusUpdate: (String) -> Unit) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        val hasFineLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarseLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (hasFineLocation || hasCoarseLocation) {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location: android.location.Location? ->
                    val locLink = if (location != null) "https://www.google.com/maps/search/?api=1&query=${location.latitude},${location.longitude}"
                    else "Location Unavailable"
                    postToEmailJS(context, incidentType, targetDept, targetEmail, bitmap, locLink, onStatusUpdate)
                }
                .addOnFailureListener { postToEmailJS(context, incidentType, targetDept, targetEmail, bitmap, "Location Error", onStatusUpdate) }
        } else {
            postToEmailJS(context, incidentType, targetDept, targetEmail, bitmap, "Permission Denied", onStatusUpdate)
        }
    }

    private fun postToEmailJS(context: android.content.Context, type: String, targetDept: String, targetEmail: String, bitmap: Bitmap, mapsLink: String, onStatusUpdate: (String) -> Unit) {
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
                put("target_department", targetDept)
                put("to_email", targetEmail)
                put("location_link", mapsLink)
                put("device_info", "${Build.MANUFACTURER} ${Build.MODEL}")
                put("image_data", "data:image/jpeg;base64,$base64Data")
            })
        }

        val queue = Volley.newRequestQueue(context)
        val request = object : JsonObjectRequest(Method.POST, "https://api.emailjs.com/api/v1.0/email/send", json,
            { 
                Log.d("UGSAFE", "EmailJS Success")
                onStatusUpdate("Report delivered! $targetDept has been notified and help is being dispatched.")
            },
            { 
                Log.e("UGSAFE", "EmailJS Error")
                onStatusUpdate("Alert failed to send automatically. Please dial 999 immediately.")
            }
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
            label.equals("fire", true) -> "Fire-related visuals detected (${(score * 100).toInt()}%). Monitoring situation."
            label.equals("accident", true) -> "Potential traffic incident detected (${(score * 100).toInt()}%). Monitoring situation."
            label.equals("no_fire", true) -> "The scene appears safe from thermal hazards."
            label.equals("no_accident", true) -> "Traffic flow or the scene appears normal."
            label.equals("neutral", true) -> "The environment is stable and safe."
            else -> "Everything looks safe. The UGsafe AI will help you incase of emergency."
        }
    }

    private fun runInference(bitmap: Bitmap): String {
        return try {
            val classifier = ImageClassifier.createFromFileAndOptions(this, "incident_detector.tflite", ImageClassifier.ImageClassifierOptions.builder().setMaxResults(1).setScoreThreshold(0.1f).build())
            val results = classifier.classify(TensorImage.fromBitmap(bitmap))
            val top = results.firstOrNull()?.categories?.firstOrNull()
            if (top != null) "${top.label} (${(top.score * 100).toInt()}%)" else "Normal (0%)"
        } catch (e: Exception) { "Error (0%)" }
    }
}

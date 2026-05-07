package com.example.ugsafe

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.widget.Toast
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import java.io.OutputStream
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
import androidx.compose.material.icons.outlined.HelpOutline
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
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.example.ugsafe.ui.theme.*
import com.google.android.gms.location.LocationServices
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.io.FileInputStream
import java.io.ByteArrayOutputStream
import java.util.Locale
import kotlin.math.exp

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
        
        // Enable showing over lockscreen for emergency access
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        createNotificationChannel()
        showQuickAccessNotification()

        setContent {
            UgsafeTheme {
                MainScreen()
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Emergency Quick Access"
            val descriptionText = "Persistent notification for fast emergency scanning"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel("EMERGENCY_CHANNEL", name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showQuickAccessNotification() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, "EMERGENCY_CHANNEL")
            .setSmallIcon(R.drawable.ug_safe_app_icon)
            .setContentTitle("UgSafe Emergency Monitor")
            .setContentText("Tap for instant emergency scan")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setOngoing(true) // Persistent

        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1001, builder.build())
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
        var showUserGuide by remember { mutableStateOf(false) }

        if (showUserGuide) {
            UserGuideDialog(onDismiss = { showUserGuide = false })
        }

        val cameraLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.TakePicturePreview()
        ) { bitmap ->
            if (bitmap != null) {
                capturedBitmap = bitmap
                saveBitmapToGallery(context, bitmap)
                val rawOutput = runInference(bitmap)
                rawInference = rawOutput
                
                // More robust parsing
                val label = rawOutput.substringBefore(" (").trim().lowercase()
                val score = (rawOutput.substringAfter("(", "0").substringBefore("%").toFloatOrNull() ?: 0f) / 100f

                val isFire = label == "fire_images"
                val isAccident = label == "accidents"
                
                Log.d("UGSAFE_AI", "Parsed Label: '$label', Score: $score, isFire: $isFire, isAccident: $isAccident")
                
                isEmergency = (isFire || isAccident) && score >= 0.5f

                if (isEmergency) {
                    val targetDept = if (isFire) "Fire Brigade" else "Police Department"
                    val targetEmail = if (isFire) FIRE_BRIGADE_EMAIL else POLICE_DEPT_EMAIL
                    
                    classificationResult = if (isFire) "FIRE ACCIDENT" else "ROAD ACCIDENT"
                    classificationDetail = if (isFire) {
                        "ACTION TAKEN: Emergency alert with your location has been sent to the Fire Brigade.\n\n" +
                        "STRANGER'S GUIDE: We have identified your location and notified the nearest fire station. Use the 'NAVIGATE' button below to find the fastest route to safety if you are unfamiliar with this area."
                    } else {
                        "ACTION TAKEN: A distress signal has been sent to the Police. We have prepared the fastest route to the nearest hospital.\n\n" +
                        "STRANGER'S GUIDE: Stay calm. We have located the nearest hospital for you. Click 'NAVIGATE' for turn-by-turn directions to get there safely, even if you are a stranger to this place."
                    }
                    
                    reportStatus = "Routing distress signal to $targetDept..."
                    
                    // Generate Directions URL (Hospital for Road Accident, Fire Station for Fire)
                    val searchType = if (isFire) "fire+station" else "hospital"
                    generateDirectionsUrl(context, searchType) { url ->
                        nearbyHelpUrl = url
                        nearbyHelpType = if (isFire) "Fire Station" else "Hospital"
                    }

                    sendAnonymousReport(context, if(isFire) "Fire" else "Road Accident", targetDept, targetEmail, bitmap) { status ->
                        reportStatus = status
                    }
                } else {
                    classificationResult = "NO ACCIDENT DETECTED"
                    classificationDetail = "ACTION TAKEN: AI monitor is active. No immediate threats detected in this scan.\n\n" +
                                         "USER GUIDANCE: The environment appears safe. Stay alert and use 'Quick Assistance' if you notice any other dangers."
                    reportStatus = ""
                    nearbyHelpUrl = ""
                    nearbyHelpType = ""
                }
            }
        }

        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            if (permissions[Manifest.permission.POST_NOTIFICATIONS] == true) {
                showQuickAccessNotification()
            }
        }

        LaunchedEffect(Unit) {
            checkLocationStatus(context) { status -> locationStatus = status }

            val permissions = mutableListOf(
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            permissionLauncher.launch(permissions.toTypedArray())
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
                    
                    IconButton(
                        onClick = { showUserGuide = true },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.05f))
                    ) {
                        Icon(
                            Icons.Default.HelpOutline,
                            contentDescription = "User Guide",
                            tint = Color.White
                        )
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
                            Text("Our AI will analyze the scene for emergencies", color = TextGray, fontSize = 13.sp)
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
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("STRANGER'S EMERGENCY GUIDE", color = TextGray, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(nearbyHelpUrl))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth().height(70.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Directions, contentDescription = null, modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("NAVIGATE TO NEAREST ${nearbyHelpType.uppercase()}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("Get live turn-by-turn guidance now", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                val sendIntent: Intent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    val locMsg = if (locationStatus != "Location Active" && locationStatus != "Locating device...") "at $locationStatus" else "at my current location"
                                    putExtra(Intent.EXTRA_TEXT, "I am in an emergency ($classificationResult) $locMsg. Please send help! Map: $nearbyHelpUrl")
                                    type = "text/plain"
                                }
                                val shareIntent = Intent.createChooser(sendIntent, null)
                                context.startActivity(shareIntent)
                            },
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.05f)),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Share Route", fontSize = 13.sp)
                        }

                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:999"))
                                context.startActivity(intent)
                            },
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentRed.copy(alpha = 0.1f)),
                            border = BorderStroke(1.dp, AccentRed.copy(alpha = 0.2f))
                        ) {
                            Icon(Icons.Default.Call, contentDescription = null, tint = AccentRed, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Call 999", color = AccentRed, fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
                            Text("Start AI-powered incident detection to report", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
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

                // Quick Access Hint
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.TouchApp, contentDescription = null, tint = Color(0xFFBB86FC), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Pro Tip: Add the 'UgSafe Scan' tile to your phone's Quick Settings for instant emergency access, even from the lock screen.",
                            color = TextGray,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NearbyServiceCard(
                        title = "Hospital",
                        icon = Icons.Default.LocalHospital,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val url = "https://www.google.com/maps/dir/?api=1&destination=hospital&travelmode=driving"
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }
                    )
                    NearbyServiceCard(
                        title = "Police",
                        icon = Icons.Default.LocalPolice,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val url = "https://www.google.com/maps/dir/?api=1&destination=police+station&travelmode=driving"
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }
                    )
                    NearbyServiceCard(
                        title = "Fire",
                        icon = Icons.Default.FireTruck,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val url = "https://www.google.com/maps/dir/?api=1&destination=fire+station&travelmode=driving"
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
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
    fun UserGuideDialog(onDismiss: () -> Unit) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = onDismiss,
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = DarkBg
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("USER GUIDE & MANUAL", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    GuideSection(
                        title = "1. QUICK SETUP",
                        content = "Add the 'UgSafe Scan' tile to your phone's Quick Settings. This allows you to launch the scanner instantly, even from the lock screen.",
                        icon = Icons.Default.Settings
                    )

                    GuideSection(
                        title = "2. AI SCANNER",
                        content = "Tap 'SCAN NOW' to take a photo of an accident or fire. Our AI will analyze the scene and automatically notify the relevant authorities with your exact location.",
                        icon = Icons.Default.CameraAlt
                    )

                    GuideSection(
                        title = "3. STRANGER'S GUIDE",
                        content = "If you are in an unfamiliar place, use the 'NAVIGATE' button after a detection. It calculates the SHORTEST route to the nearest hospital or fire station.",
                        icon = Icons.Default.Directions
                    )

                    GuideSection(
                        title = "4. SHARE LOCATION",
                        content = "Use 'Share Route' to send your current coordinates and the emergency details to your contacts immediately.",
                        icon = Icons.Default.Share
                    )

                    Spacer(modifier = Modifier.height(32.dp))
                    
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("GOT IT", fontWeight = FontWeight.Bold)
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    @Composable
    fun GuideSection(title: String, content: String, icon: ImageVector) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = AccentRed, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(content, color = TextGray, fontSize = 14.sp, lineHeight = 20.sp)
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
            if (location != null) {
                try {
                    val geocoder = android.location.Geocoder(context, Locale.getDefault())
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                    if (addresses != null && addresses.isNotEmpty()) {
                        val address = addresses[0].getAddressLine(0)
                        onResult(address)
                    } else {
                        onResult("Lat: ${String.format(Locale.US, "%.4f", location.latitude)}, Lon: ${String.format(Locale.US, "%.4f", location.longitude)}")
                    }
                } catch (e: Exception) {
                    onResult("Location Active")
                }
            } else {
                onResult("Location Unknown")
            }
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
        val request = object : StringRequest(Request.Method.POST, "https://api.emailjs.com/api/v1.0/email/send",
            { response ->
                Log.d("UGSAFE", "EmailJS Success: $response")
                onStatusUpdate("Report delivered! $targetDept has been notified and help is being dispatched.")
            },
            { error ->
                Log.e("UGSAFE", "EmailJS Error: ${error.message}")
                // Check if it's a false negative (EmailJS sometimes returns "OK" which Volley can't parse as JSON if using JsonObjectRequest, but here we use StringRequest)
                onStatusUpdate("Alert failed to send automatically. Please dial 999 immediately.")
            }
        ) {
            override fun getBody(): ByteArray = json.toString().toByteArray(Charsets.UTF_8)
            override fun getBodyContentType(): String = "application/json; charset=utf-8"

            override fun getHeaders(): MutableMap<String, String> {
                val headers = HashMap<String, String>()
                headers["Origin"] = "http://localhost"
                return headers
            }
        }
        queue.add(request)
    }

    private fun saveBitmapToGallery(context: Context, bitmap: Bitmap) {
        val filename = "UGSafe_${System.currentTimeMillis()}.jpg"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/UGSafe")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val contentResolver = context.contentResolver
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        uri?.let {
            try {
                contentResolver.openOutputStream(it)?.use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    contentResolver.update(it, contentValues, null, null)
                }
                Log.d("UGSAFE_STORAGE", "Image saved to gallery: $uri")
            } catch (e: Exception) {
                Log.e("UGSAFE_STORAGE", "Failed to save image", e)
                Toast.makeText(context, "Failed to save image for debugging", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun runInference(bitmap: Bitmap): String {
        return try {
            // 1. Explicit Model Loading
            val modelFile = this.assets.openFd("incident_detector.tflite")
            val inputStream = FileInputStream(modelFile.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = modelFile.startOffset
            val declaredLength = modelFile.declaredLength
            val modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)

            val interpreter = Interpreter(modelBuffer)

            // 2. Strict Input Buffer Prep (224x224x3 Float32)
            val inputBuffer = ByteBuffer.allocateDirect(1 * 224 * 224 * 3 * 4)
            inputBuffer.order(ByteOrder.nativeOrder())

            // Force ARGB_8888 for strict pixel extraction and skip Alpha channel
            val argbBitmap = if (bitmap.config != Bitmap.Config.ARGB_8888) {
                bitmap.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                bitmap
            }

            // Resize to 224x224 (Bilinear filtering)
            val scaledBitmap = Bitmap.createScaledBitmap(argbBitmap, 224, 224, true)
            val intValues = IntArray(224 * 224)
            scaledBitmap.getPixels(intValues, 0, 224, 0, 0, 224, 224)

            // 3. Normalization: (pixel - 127.5) / 127.5 -> Range [-1, 1]
            inputBuffer.rewind()
            for (pixel in intValues) {
                // Extract R, G, B and discard Alpha (bits 24-31)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                
                // Strict Float32 math
                inputBuffer.putFloat((r.toFloat() - 127.5f) / 127.5f)
                inputBuffer.putFloat((g.toFloat() - 127.5f) / 127.5f)
                inputBuffer.putFloat((b.toFloat() - 127.5f) / 127.5f)
            }

            // 4. Output Handling (Matches your 5-class model)
            val output = Array(1) { FloatArray(5) }
            
            // 5. Run Inference
            interpreter.run(inputBuffer, output)

            // 6. Read Results
            val rawResults = output[0]
            val classLabels = listOf("accidents", "fire_images", "neutral", "non_accident", "non_fire_images")
            
            // Debug Logs: Observe raw values from the model
            rawResults.forEachIndexed { index, score ->
                Log.d("UGSAFE_DEBUG", "Class: ${classLabels.getOrElse(index) { "#$index" }}, Raw Score: $score")
            }

            // Argmax (We use the scores directly since your logs show they are already probabilities)
            var maxIdx = 0
            var maxProb = -1f
            for (i in rawResults.indices) {
                if (rawResults[i] > maxProb) {
                    maxProb = rawResults[i]
                    maxIdx = i
                }
            }

            val topLabel = classLabels.getOrElse(maxIdx) { "unknown" }
            interpreter.close()
            
            Log.d("UGSAFE_AI", "Final Decision: $topLabel ($maxProb)")

            "${topLabel.replace("_", " ").uppercase()} (${(maxProb * 100).toInt()}%)"
        } catch (e: Exception) {
            Log.e("UGSAFE_AI", "Inference error: ${e.message}", e)
            "Error (0%)"
        }
    }
}

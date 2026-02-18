package com.example.ronnie

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onNavigateToCamera: () -> Unit) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Incident Reporter", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // --- TOP QUARTER: LOCAL IMAGE SECTION ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.25f)
            ) {
                AsyncImage(
                    // CHANGE: model now points to your local drawable file
                    // Make sure the name matches the file you pasted (without extension)
                    model = R.drawable.incident_header,
                    contentDescription = "Incident Background",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                Surface(
                    color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.2f),
                    modifier = Modifier.fillMaxSize()
                ) {}
            }

            // --- BOTTOM THREE QUARTERS: CONTENT ---
            Column(
                modifier = Modifier
                    .weight(0.75f)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.error
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Report an Incident",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Follow these steps:",
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Text("1. Open camera and take photo.")
                            Text("2. Tag incident location.")
                            Text("3. Describe and submit.")
                        }
                    }
                }

                Button(
                    onClick = onNavigateToCamera,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("START REPORTING", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
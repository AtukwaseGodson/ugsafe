package com.example.ronnie

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun PreviewScreen(
    imageUri: Uri,
    onRetake: () -> Unit,
    onConfirm: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black), // Black background makes the photo pop
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Header Text
        Text(
            text = "Review Incident Photo",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            modifier = Modifier.padding(top = 48.dp, bottom = 16.dp)
        )

        // 2. The Photo (using Coil's AsyncImage)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            AsyncImage(
                model = imageUri,
                contentDescription = "Captured Incident",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }

        // 3. Action Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // RETAKE BUTTON
            OutlinedButton(
                onClick = onRetake,
                modifier = Modifier.height(56.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(Color.White))
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Retake")
            }

            // CONFIRM BUTTON
            Button(
                onClick = onConfirm,
                modifier = Modifier.height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50), // Green
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Confirm Report")
            }
        }
    }
}
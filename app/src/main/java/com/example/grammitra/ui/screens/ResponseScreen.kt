package com.example.grammitra.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.navigation.NavController

@Composable
fun ResponseScreen(navController: NavController) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("AI Response", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(18.dp))
        // Voice response
        OutlinedButton(onClick = { /* TODO: Play audio */ }) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Play Audio")
            Spacer(Modifier.width(8.dp))
            Text("Listen in Hindi")
        }
        Spacer(Modifier.height(10.dp))
        // Text response
        Text("It looks like your crop may be affected by yellow stem borer. Suggested action: Apply neem-based spray at 2ml/litre. Contact your local Krishi center for more help.", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        // Video response
        OutlinedButton(onClick = { /* TODO: Play video */ }) {
            Icon(Icons.Default.VideoLibrary, contentDescription = "Play Video")
            Spacer(Modifier.width(8.dp))
            Text("Watch Demo Video")
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { navController.navigate("home") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back to Home")
        }
    }
}

package com.example.grammitra.ui.components

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings

@Composable
fun BottomNavBar(navController: NavController) {
    NavigationBar {
        NavigationBarItem(
            icon = { Icon(Icons.Filled.Home, contentDescription = "Home") },
            label = { Text("Home") },
            selected = false,
            onClick = { navController.navigate("home") }
        )
        NavigationBarItem(
            icon = { Icon(Icons.Filled.Mic, contentDescription = "Ask") },
            label = { Text("Ask a Question") },
            selected = false,
            onClick = { navController.navigate("ask_question") }
        )
        NavigationBarItem(
            icon = { Icon(Icons.Filled.History, contentDescription = "History") },
            label = { Text("History") },
            selected = false,
            onClick = { navController.navigate("history") }
        )
        NavigationBarItem(
            icon = { Icon(Icons.Filled.Settings, contentDescription = "Settings") },
            label = { Text("Settings") },
            selected = false,
            onClick = { navController.navigate("settings") }
        )
    }
}

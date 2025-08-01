package com.example.grammitra

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.*
import com.example.grammitra.ui.components.BottomNavBar
import com.example.grammitra.ui.screens.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GramMitraApp()
        }
    }
}

@Composable
fun GramMitraApp() {
    val navController = rememberNavController()
    Scaffold(
        bottomBar = { BottomNavBar(navController) }
    ) { innerPadding ->
        NavHost(
            navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("home") { HomeScreen(navController) }
            composable("ask_question") { AskQuestionScreen(navController) }
            composable("history") { HistoryScreen(navController) }
            composable("settings") { SettingsScreen(navController) }
            composable("response") { ResponseScreen(navController) }
        }
    }
}

package com.example.grammitra.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Email
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController


@Composable
fun SettingsScreen(navController: NavController) {
    var selectedLang by remember { mutableStateOf("English") }
    val languages = listOf("English", "हिन्दी", "বাংলা", "मराठी", "தமிழ்")
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(10.dp))

        // Language selection
        Card {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Language, contentDescription = "Language", Modifier.size(32.dp))
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    Text("App Language", style = MaterialTheme.typography.bodyLarge)
                    DropdownMenuBox(
                        options = languages,
                        selected = selectedLang,
                        onOptionSelected = { selectedLang = it }
                    )
                }
            }
        }

        // About app
        Card {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Info, contentDescription = "About", Modifier.size(32.dp))
                Spacer(Modifier.width(18.dp))
                Column {
                    Text("About Gram-Mitra", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Gram-Mitra is your AI-powered assistant for agriculture and allied livelihoods, supporting multiple Indian languages and offline use.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // Contact support (optional)
        Card(
            Modifier.clickable { /* TODO: Support action, e.g., send email */ }
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Email, contentDescription = "Support", Modifier.size(32.dp))
                Spacer(Modifier.width(18.dp))
                Column {
                    Text("Contact Support", style = MaterialTheme.typography.bodyLarge)
                    Text("support@grammitra.app", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun DropdownMenuBox(
    options: List<String>,
    selected: String,
    onOptionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Button(onClick = { expanded = true }) {
            Text(selected)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

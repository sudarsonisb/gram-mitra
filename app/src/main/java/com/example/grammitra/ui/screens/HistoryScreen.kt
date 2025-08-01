package com.example.grammitra.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import java.text.SimpleDateFormat
import java.util.*

data class HistoryItem(
    val date: Date,
    val type: HistoryType,
    val summary: String
)

enum class HistoryType { VOICE, PHOTO, TEXT }

@Composable
fun HistoryScreen(navController: NavController) {
    // Mock data for now. Replace with ViewModel/live data as needed.
    val historyList = remember {
        listOf(
            HistoryItem(Date(), HistoryType.PHOTO, "Suspected yellow stem borer detected. Suggested neem spray."),
            HistoryItem(Date(System.currentTimeMillis() - 3600_000), HistoryType.VOICE, "Possible fungal infection. Spray copper oxychloride."),
            HistoryItem(Date(System.currentTimeMillis() - 86400_000), HistoryType.TEXT, "Query about government subsidy for seeds.")
        )
    }
    val dateFormatter = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())

    Column(
        Modifier.fillMaxSize().padding(16.dp)
    ) {
        Text("History", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))

        if (historyList.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No previous queries found.")
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                items(historyList.size) { idx ->
                    val item = historyList[idx]
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                    ) {
                        Row(
                            Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                when (item.type) {
                                    HistoryType.PHOTO -> Icons.Default.Photo
                                    HistoryType.VOICE -> Icons.Default.Mic
                                    HistoryType.TEXT -> Icons.Default.TextFields
                                },
                                contentDescription = item.type.name,
                                Modifier.size(36.dp)
                            )
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.summary, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    dateFormatter.format(item.date),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            IconButton(
                                onClick = { /* TODO: Re-ask logic */ navController.navigate("ask_question") }
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Re-ask")
                            }
                        }
                    }
                }
            }
        }
    }
}

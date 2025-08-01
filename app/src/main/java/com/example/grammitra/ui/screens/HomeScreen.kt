package com.example.grammitra.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.grammitra.R

@Composable
fun HomeScreen(navController: NavController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 40.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Gram-Mitra\nग्राम-मित्र", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        // Main Grid
        Column {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                HomeTile("Krishi\n(Farming)", R.drawable.ic_wheat) { navController.navigate("ask_question") }
                HomeTile("Pashu-Palan\n(Animal Care)", R.drawable.ic_animal) {}
            }
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                HomeTile("Matsya-Palan\n(Fisheries)", R.drawable.ic_fish) {}
                HomeTile("Health", R.drawable.ic_health) {}
            }
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                HomeTile("Education", R.drawable.ic_education) {}
                HomeTile("Education", R.drawable.ic_education) {}
            }
        }
    }
}

@Composable
fun HomeTile(text: String, iconRes: Int, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .width(145.dp)
            .height(120.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 4.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(painterResource(iconRes), contentDescription = null, Modifier.size(48.dp))
            Spacer(Modifier.height(8.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

package com.example.p2pchatapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.p2pchatapp.data.KeyUtils
import java.util.Base64

@Composable
fun PrivateKeyScreen() {
    val context = LocalContext.current
    val privateKey = remember { KeyUtils.getPrivateKey(context) }
    val privateKeyString = privateKey?.let { Base64.getEncoder().encodeToString(it.encoded) } ?: "No key found"

    Column(
        modifier = Modifier
            .fillMaxSize()  // Ensures the column takes up the full screen
            .padding(16.dp)  // Adds padding around the content
    ) {
        Text(
            text = "Private Key:",
            style = MaterialTheme.typography.titleLarge,  // Adjusted to titleLarge
            modifier = Modifier.padding(bottom = 8.dp)  // Adds space below the header
        )
        Text(
            text = privateKeyString,
            style = MaterialTheme.typography.bodyLarge  // Adjusted to bodyLarge
        )
    }
}

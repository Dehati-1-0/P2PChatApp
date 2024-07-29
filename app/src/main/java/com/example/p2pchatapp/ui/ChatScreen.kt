package com.example.p2pchatapp.ui

import android.net.wifi.WifiManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.p2pchatapp.model.ChatMessage
import com.example.p2pchatapp.model.DiscoveredDevice
import com.example.p2pchatapp.network.broadcastIp
import com.example.p2pchatapp.network.listenForBroadcasts
import com.example.p2pchatapp.network.sendMessage
import com.example.p2pchatapp.network.sendPing
import com.example.p2pchatapp.network.startServer
import com.example.p2pchatapp.ui.theme.P2PChatAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.timer

@Composable
fun ChatScreen(serverPort: Int, wifiManager: WifiManager?, modifier: Modifier = Modifier) {
    var message by remember { mutableStateOf("") }
    var chatLog by remember { mutableStateOf(mutableListOf<ChatMessage>()) }
    var knownPeers by remember { mutableStateOf(ConcurrentHashMap<String, DiscoveredDevice>()) }
    var discoveredDevices by remember { mutableStateOf(listOf<DiscoveredDevice>()) }
    var selectedDevice by remember { mutableStateOf<DiscoveredDevice?>(null) }
    val scope = rememberCoroutineScope()

    Column(modifier = modifier.padding(30.dp)) {
        Spacer(modifier = Modifier.height(8.dp))  // Add more space before the TextField
        TextField(
            value = message,
            onValueChange = { message = it },
            label = { Text("Enter message") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))  // Increase space after the TextField
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(onClick = {
                val currentMessage = message
                selectedDevice?.let { device ->
                    if (currentMessage.isNotEmpty()) {
                        scope.launch(Dispatchers.IO) {
                            val chatMessage = ChatMessage("You", currentMessage, true, "sent")
                            chatLog.add(chatMessage)
                            launch(Dispatchers.Main) {
                                message = "" // Clear the message input after ensuring it's sent
                            }
                            sendMessage(currentMessage, device.ip, serverPort) { success ->
                                chatMessage.status = if (success) "delivered" else "failed"
                            }
                        }
                    }
                }
            }) {
                Text("Send")
            }
            Button(onClick = {
                scope.launch(Dispatchers.IO) {
                    knownPeers.values.forEach { peer ->
                        sendPing(peer.ip, serverPort)
                    }
                }
            }) {
                Text("Ping")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Discovered Devices:", style = MaterialTheme.typography.bodySmall)
        Column {
            discoveredDevices.forEach { device ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedDevice = device
                        }
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${device.ip} - ${device.modelName}")
                    if (device == selectedDevice) {
                        Text(" (Selected)", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Column(modifier = Modifier
            .fillMaxHeight()
            .padding(16.dp)) {
            chatLog.forEach { chatMessage ->
                val status = when (chatMessage.status) {
                    "sent" -> "✓"
                    "delivered" -> "✓✓"
                    "failed" -> "✗"
                    else -> ""
                }
                val timestamp = java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date(chatMessage.timestamp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (chatMessage.isSentByUser) "${chatMessage.sender}: ${chatMessage.content} $status" else "${chatMessage.sender}: ${chatMessage.content}",
                        color = if (chatMessage.isSentByUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = timestamp,
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            startServer(serverPort) { newMessage ->
                scope.launch {
                    chatLog.add(ChatMessage("Friend", newMessage, false))
                }
            }
        }
        scope.launch(Dispatchers.IO) {
            broadcastIp(serverPort)
        }
        scope.launch(Dispatchers.IO) {
            if (wifiManager != null) {
                listenForBroadcasts(wifiManager) { discoveredDevice ->
                    knownPeers[discoveredDevice.ip] = discoveredDevice
                    scope.launch(Dispatchers.Main) {
                        discoveredDevices = knownPeers.values.toList()
                    }
                }
            }
        }

        // Heartbeat checker
        val heartbeatTimeout = 15_000L // 15 seconds
        timer(period = heartbeatTimeout) {
            val now = System.currentTimeMillis()
            knownPeers.entries.removeIf { entry ->
                now - entry.value.lastSeen > heartbeatTimeout
            }
            scope.launch(Dispatchers.Main) {
                discoveredDevices = knownPeers.values.toList()
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ChatScreenPreview() {
    P2PChatAppTheme {
        ChatScreen(serverPort = 12345, wifiManager = null)
    }
}

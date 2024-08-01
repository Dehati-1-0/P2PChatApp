package com.example.p2pchatapp.ui

import android.net.wifi.WifiManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
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

import kotlinx.coroutines.delay

@Composable
fun ChatScreen(serverPort: Int, wifiManager: WifiManager?, modifier: Modifier = Modifier) {
    var message by remember { mutableStateOf("") }
    var chatLog by remember { mutableStateOf(mutableListOf<ChatMessage>()) }
    var knownPeers by remember { mutableStateOf(ConcurrentHashMap<String, DiscoveredDevice>()) }
    var discoveredDevices by remember { mutableStateOf(listOf<DiscoveredDevice>()) }
    var selectedDevice by rememberSaveable { mutableStateOf<DiscoveredDevice?>(null) }
    var sequenceNumber by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .padding(35.dp)
            .imePadding() // Ensure content is not hidden by the keyboard
    ) {
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
                            launch(Dispatchers.Main) {
                                chatLog.add(chatMessage)
                                message = ""
                                scrollState.animateScrollTo(scrollState.maxValue)
                            }
                            sendMessage(currentMessage, device.ip, serverPort, sequenceNumber++) { status ->
                                chatMessage.status = status
                                launch(Dispatchers.Main) {
                                    chatLog = chatLog.toMutableList() // Trigger recomposition
                                    scrollState.animateScrollTo(scrollState.maxValue)
                                }
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
                        .padding(8.dp)
                        .background(if (device == selectedDevice) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.background),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${device.ip} - ${device.modelName}")
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(16.dp)
                .verticalScroll(scrollState)
        ) {
            chatLog.forEach { chatMessage ->
                val status = when (chatMessage.status) {
                    "sent" -> "✓"
                    "delivered" -> "✓✓"
                    "seen" -> "✓✓"
                    "failed" -> "✗"
                    else -> ""
                }
                val statusColor = when (chatMessage.status) {
                    "seen" -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onBackground
                }
                val timestamp = java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date(chatMessage.timestamp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val senderName = if (chatMessage.isSentByUser) "You" else knownPeers[chatMessage.sender]?.modelName ?: chatMessage.sender
                    Text(
                        text = if (chatMessage.isSentByUser) "$senderName: ${chatMessage.content} $status" else "$senderName: ${chatMessage.content}",
                        color = if (chatMessage.isSentByUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = timestamp,
                        color = statusColor,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }

    LaunchedEffect(chatLog.size) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            startServer(serverPort, { newMessage, senderIp ->
                scope.launch {
                    chatLog.add(ChatMessage(senderIp, newMessage, false))
                    chatLog = chatLog.toMutableList() // Trigger recomposition
                    scrollState.animateScrollTo(scrollState.maxValue)
                }
            }, { seenMessage ->
                scope.launch {
                    chatLog.find { it.content == seenMessage }?.status = "seen"
                    chatLog = chatLog.toMutableList() // Trigger recomposition
                    scrollState.animateScrollTo(scrollState.maxValue)
                }
            })
        }
        scope.launch(Dispatchers.IO) {
            while (true) {
                broadcastIp(serverPort)
                delay(5000) // Adjust the interval as needed
            }
        }
        scope.launch(Dispatchers.IO) {
            if (wifiManager != null) {
                while (true) {
                    listenForBroadcasts(wifiManager) { discoveredDevice ->
                        knownPeers[discoveredDevice.ip] = discoveredDevice
                        scope.launch(Dispatchers.Main) {
                            discoveredDevices = knownPeers.values.toList()
                        }
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
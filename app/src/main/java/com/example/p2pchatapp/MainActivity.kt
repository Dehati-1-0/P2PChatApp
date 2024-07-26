package com.example.p2pchatapp

import android.annotation.SuppressLint
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.p2pchatapp.ui.theme.P2PChatAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.timer
import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

private const val PREFS_NAME = "P2PChatAppPrefs"
private const val OFFLINE_MESSAGES_KEY = "OfflineMessages"


class MainActivity : ComponentActivity() {
    private val serverPort = 12345
    private lateinit var wifiManager: WifiManager
    private lateinit var offlineMessages: ConcurrentHashMap<String, MutableList<OfflineMessage>>

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        offlineMessages = retrieveOfflineMessages(this)
        enableEdgeToEdge()
        setContent {
            P2PChatAppTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ChatScreen(
                        serverPort = serverPort,
                        wifiManager = wifiManager,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        storeOfflineMessages(this, offlineMessages)
    }
}

@SuppressLint("MutableCollectionMutableState")
@Composable
fun ChatScreen(serverPort: Int, wifiManager: WifiManager?, modifier: Modifier = Modifier) {
    var message by remember { mutableStateOf("") }

//     var chatLogs by remember { mutableStateOf(mutableMapOf<String, String>()) }
//     var knownPeers by remember { mutableStateOf(mutableSetOf<DiscoveredDevice>()) }

    var chatLog by remember { mutableStateOf(mutableListOf<ChatMessage>()) }
    var knownPeers by remember { mutableStateOf(ConcurrentHashMap<String, DiscoveredDevice>()) }

    var discoveredDevices by remember { mutableStateOf(listOf<DiscoveredDevice>()) }
    var selectedDevice by remember { mutableStateOf<DiscoveredDevice?>(null) }
    val scope = rememberCoroutineScope()

    Column(modifier = modifier.padding(16.dp)) {
        TextField(
            value = message,
            onValueChange = { message = it },
            label = { Text("Enter message") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
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
                                updateChatLog(chatLogs, device.ip, "Me: $currentMessage")
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
                            // Switch chat log to the selected device's log
                            chatLogs[device.ip]?.let {
                                // Ensure chat log is updated on UI thread
                                chatLogs = chatLogs.toMutableMap().apply { put(device.ip, it) }
                            }
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

//         Text(chatLogs[selectedDevice?.ip] ?: "Chat Log:", modifier = Modifier

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
            startServer(serverPort) { ip, newMessage ->
                scope.launch {

//                     val sender = if (ip == selectedDevice?.ip) "Them: " else "Unknown: "
//                     updateChatLog(chatLogs, ip, "$sender$newMessage")

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

fun updateChatLog(chatLogs: MutableMap<String, String>, ip: String, newMessage: String) {
    chatLogs[ip] = (chatLogs[ip] ?: "Chat Log:") + "\n$newMessage"
}

data class DiscoveredDevice(
    val ip: String,
    val modelName: String,
    var lastSeen: Long = System.currentTimeMillis()
)

data class ChatMessage(
    val sender: String,
    val content: String,
    val isSentByUser: Boolean,
    var status: String = "sent", // Default status is 'sent'
    val timestamp: Long = System.currentTimeMillis() // Add timestamp
)

data class OfflineMessage(
    val sender: String,
    val content: String,
    val timestamp: Long
)

val offlineMessages = ConcurrentHashMap<String, MutableList<OfflineMessage>>()

private fun sendMessage(message: String, serverIp: String, serverPort: Int, callback: (Boolean) -> Unit) {
    val timestamp = System.currentTimeMillis()
    Thread {
        try {
            val socket = Socket(serverIp, serverPort)
            val writer = PrintWriter(socket.getOutputStream(), true)
            writer.println(message)
            writer.flush()

            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            socket.soTimeout = 5000
            val response = reader.readLine()
            if (response == "ACK") {
                callback(true) // Message delivered successfully
            } else {
                callback(false) // Message delivery failed
            }
            socket.close()
        } catch (e: Exception) {
            // Store message for offline peer
            offlineMessages.computeIfAbsent(serverIp) { mutableListOf() }
                .add(OfflineMessage("You", message, timestamp))
            callback(false) // Message delivery failed
        }
    }.start()
}

private fun sendPing(serverIp: String, serverPort: Int) {
    try {
        Log.d("P2PChatApp", "Sending ping to $serverIp:$serverPort")
        val socket = Socket(serverIp, serverPort)
        val writer = PrintWriter(socket.getOutputStream(), true)
        writer.println("ping")
        writer.flush()  // Ensure the ping is sent immediately
        socket.close()
        Log.d("P2PChatApp", "Ping sent successfully")
    } catch (e: Exception) {
        e.printStackTrace()
        Log.e("P2PChatApp", "Error sending ping: ${e.message}")
    }
}


// private fun startServer(port: Int, onMessageReceived: (String, String) -> Unit) {
//     try {
//         Log.d("P2PChatApp", "Starting server on port $port")
//         val serverSocket = ServerSocket(port)
//         while (true) {
//             val clientSocket = serverSocket.accept()
//             val clientIp = clientSocket.inetAddress.hostAddress
//             val reader = BufferedReader(InputStreamReader(clientSocket.getInputStream()))
//             val message = reader.readLine()
//             Log.d("P2PChatApp", "Received message from $clientIp: $message")
//             if (message != null) {
//                 onMessageReceived(clientIp, message)
//             } else {
//                 Log.d("P2PChatApp", "Received empty message")

private fun startServer(port: Int, onMessageReceived: (String) -> Unit) {
    Thread {
        try {
            val serverSocket = ServerSocket(port)
            while (true) {
                val clientSocket = serverSocket.accept()
                val clientIp = clientSocket.inetAddress.hostAddress
                val reader = BufferedReader(InputStreamReader(clientSocket.getInputStream()))
                val message = reader.readLine()
                if (message != null) {
                    onMessageReceived(message)
                    val writer = PrintWriter(clientSocket.getOutputStream(), true)
                    writer.println("ACK")
                    writer.flush()
                }
                clientSocket.close()


                // Deliver stored messages in order of their timestamp
                offlineMessages[clientIp]?.let { messages ->
                    messages.sortedBy { it.timestamp }.forEach { offlineMessage ->
                        sendMessage(offlineMessage.content, clientIp, port) { success ->
                            if (success) {
                                messages.remove(offlineMessage)
                            }
                        }
                    }
                }

            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("P2PChatApp", "Error starting server: ${e.message}")
        }
    }.start()
}

private fun broadcastIp(port: Int) {
    try {
        val broadcastAddress = InetAddress.getByName("255.255.255.255")
        val socket = DatagramSocket()
        val localIpAddress = getLocalIpAddress() ?: return
        val message = "DISCOVER:$localIpAddress:${getDeviceModelName()}"
        val packet = DatagramPacket(message.toByteArray(), message.length, broadcastAddress, port)
        while (true) {
            socket.send(packet)
            Thread.sleep(5000) // Broadcast every 5 seconds
        }
    } catch (e: Exception) {
        e.printStackTrace()
        Log.e("P2PChatApp", "Error broadcasting IP: ${e.message}")
    }
}

private fun listenForBroadcasts(wifiManager: WifiManager, onDeviceDiscovered: (DiscoveredDevice) -> Unit) {
    try {
        val socket = DatagramSocket(12345, InetAddress.getByName("0.0.0.0"))
        socket.broadcast = true
        val buffer = ByteArray(1024)
        val localIpAddress = getLocalIpAddress() ?: return
        wifiManager.createMulticastLock("p2pchatapp").apply {
            setReferenceCounted(true)
            acquire()
        }
        while (true) {
            val packet = DatagramPacket(buffer, buffer.size)
            socket.receive(packet)
            val message = String(packet.data, 0, packet.length)
            if (message.startsWith("DISCOVER:") && !message.contains(localIpAddress)) {
                val parts = message.split(":")
                val ip = parts[1]
                val modelName = parts[2]
                val device = DiscoveredDevice(ip, modelName)
                Log.d("P2PChatApp", "Discovered device: $device")
                onDeviceDiscovered(device)
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        Log.e("P2PChatApp", "Error listening for broadcasts: ${e.message}")
    }
}

private fun getLocalIpAddress(): String? {
    return try {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        while (interfaces.hasMoreElements()) {
            val networkInterface = interfaces.nextElement()
            val addresses = networkInterface.inetAddresses
            while (addresses.hasMoreElements()) {
                val address = addresses.nextElement()
                if (!address.isLoopbackAddress && address is Inet4Address) {
                    return address.hostAddress
                }
            }
        }
        null
    } catch (e: Exception) {
        e.printStackTrace()
        Log.e("P2PChatApp", "Error getting local IP address: ${e.message}")
        null
    }
}

private fun getDeviceModelName(): String {
    return Build.MODEL ?: "Unknown"
}

fun storeOfflineMessages(context: Context, messages: ConcurrentHashMap<String, MutableList<OfflineMessage>>) {
    val sharedPreferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val editor = sharedPreferences.edit()
    val gson = Gson()
    val json = gson.toJson(messages)
    editor.putString(OFFLINE_MESSAGES_KEY, json)
    editor.apply()
}

fun retrieveOfflineMessages(context: Context): ConcurrentHashMap<String, MutableList<OfflineMessage>> {
    val sharedPreferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val gson = Gson()
    val json = sharedPreferences.getString(OFFLINE_MESSAGES_KEY, null)
    val type = object : TypeToken<ConcurrentHashMap<String, MutableList<OfflineMessage>>>() {}.type
    return if (json != null) {
        gson.fromJson(json, type)
    } else {
        ConcurrentHashMap()
    }
}

@Preview(showBackground = true)
@Composable
fun ChatScreenPreview() {
    P2PChatAppTheme {
        ChatScreen(serverPort = 12345, wifiManager = null)
    }
}
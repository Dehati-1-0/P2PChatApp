package com.example.p2pchatapp

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

class MainActivity : ComponentActivity() {
    private val serverPort = 12345
    private lateinit var wifiManager: WifiManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
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
}

@Composable
fun ChatScreen(serverPort: Int, wifiManager: WifiManager?, modifier: Modifier = Modifier) {
    var message by remember { mutableStateOf("") }
    var chatLogs by remember { mutableStateOf(mutableMapOf<String, String>()) }
    var knownPeers by remember { mutableStateOf(mutableSetOf<DiscoveredDevice>()) }
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
                            sendMessage(currentMessage, device.ip, serverPort)
                            launch(Dispatchers.Main) {
                                message = "" // Clear the message input after ensuring it's sent
                                updateChatLog(chatLogs, device.ip, "Me: $currentMessage")
                            }
                        }
                    }
                }
            }) {
                Text("Send")
            }
            Button(onClick = {
                scope.launch(Dispatchers.IO) {
                    knownPeers.forEach { peer ->
                        sendPing(peer.ip, serverPort)
                    }
                }
            }) {
                Text("Ping")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = {
            discoveredDevices = knownPeers.map { it.copy() }
        }) {
            Text("Discover")
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
        Text(chatLogs[selectedDevice?.ip] ?: "Chat Log:", modifier = Modifier
            .fillMaxHeight()
            .padding(16.dp))
    }

    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            startServer(serverPort) { ip, newMessage ->
                scope.launch {
                    val sender = if (ip == selectedDevice?.ip) "Them: " else "Unknown: "
                    updateChatLog(chatLogs, ip, "$sender$newMessage")
                }
            }
        }
        scope.launch(Dispatchers.IO) {
            broadcastIp(serverPort)
        }
        scope.launch(Dispatchers.IO) {
            if (wifiManager != null) {
                listenForBroadcasts(wifiManager) { discoveredDevice ->
                    if (!knownPeers.any { it.ip == discoveredDevice.ip }) {
                        knownPeers.add(discoveredDevice)
                        Log.d("P2PChatApp", "Discovered peer IP: ${discoveredDevice.ip}, Model: ${discoveredDevice.modelName}")
                    }
                    scope.launch(Dispatchers.Main) {
                        discoveredDevices = knownPeers.toList()
                    }
                }
            }
        }
    }
}

fun updateChatLog(chatLogs: MutableMap<String, String>, ip: String, newMessage: String) {
    chatLogs[ip] = (chatLogs[ip] ?: "Chat Log:") + "\n$newMessage"
}

data class DiscoveredDevice(
    val ip: String,
    val modelName: String
)

private fun sendMessage(message: String, serverIp: String, serverPort: Int) {
    try {
        Log.d("P2PChatApp", "Attempting to connect to $serverIp:$serverPort")
        val socket = Socket(serverIp, serverPort)
        Log.d("P2PChatApp", "Connected to $serverIp:$serverPort")
        val writer = PrintWriter(socket.getOutputStream(), true)
        writer.println(message)
        writer.flush()  // Ensure the message is sent immediately
        Log.d("P2PChatApp", "Message sent: $message")
        socket.close()
        Log.d("P2PChatApp", "Socket closed after sending message")
    } catch (e: Exception) {
        e.printStackTrace()
        Log.e("P2PChatApp", "Error sending message: ${e.message}")
    }
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

private fun startServer(port: Int, onMessageReceived: (String, String) -> Unit) {
    try {
        Log.d("P2PChatApp", "Starting server on port $port")
        val serverSocket = ServerSocket(port)
        while (true) {
            val clientSocket = serverSocket.accept()
            val clientIp = clientSocket.inetAddress.hostAddress
            val reader = BufferedReader(InputStreamReader(clientSocket.getInputStream()))
            val message = reader.readLine()
            Log.d("P2PChatApp", "Received message from $clientIp: $message")
            if (message != null) {
                onMessageReceived(clientIp, message)
            } else {
                Log.d("P2PChatApp", "Received empty message")
            }
            clientSocket.close()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        Log.e("P2PChatApp", "Error starting server: ${e.message}")
    }
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
            if (message.startsWith("DISCOVER:")) {
                val data = message.substringAfter("DISCOVER:").split(":")
                val ip = data[0]
                val modelName = data[1]
                if (ip != localIpAddress) {
                    val device = DiscoveredDevice(ip, modelName)
                    onDeviceDiscovered(device)
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        Log.e("P2PChatApp", "Error listening for broadcasts: ${e.message}")
    }
}

private fun getLocalIpAddress(): String? {
    try {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        for (networkInterface in interfaces) {
            val addresses = networkInterface.inetAddresses
            for (address in addresses) {
                if (!address.isLoopbackAddress && address is Inet4Address) {
                    return address.hostAddress
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        Log.e("P2PChatApp", "Error getting local IP address: ${e.message}")
    }
    return null
}

private fun getDeviceModelName(): String {
    return "${Build.MANUFACTURER} ${Build.MODEL}"
}

@Preview(showBackground = true)
@Composable
fun ChatScreenPreview() {
    P2PChatAppTheme {
        ChatScreen(serverPort = 12345, wifiManager = null) // This IP is just for preview purposes
    }
}

package com.example.p2pchatapp

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        startService(Intent(this, BroadcastService::class.java))
        setContent {
            P2PChatAppTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ChatScreen(
                        serverPort = serverPort,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@SuppressLint("DefaultLocale")
@Composable
fun ChatScreen(serverPort: Int, modifier: Modifier = Modifier) {
    var message by remember { mutableStateOf("") }
    var chatLog by remember { mutableStateOf("Chat Log:") }
    val scope = rememberCoroutineScope()
    var discoveredDevices by remember { mutableStateOf(listOf<String>()) }
    var selectedDevice by remember { mutableStateOf("") }
    var showDropdown by remember { mutableStateOf(false) }
    val context = LocalContext.current

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
                if (currentMessage.isNotEmpty() && selectedDevice.isNotEmpty()) {
                    scope.launch(Dispatchers.IO) {
                        sendMessage(currentMessage, selectedDevice, serverPort)
                        launch(Dispatchers.Main) {
                            message = "" // Clear the message input after ensuring it's sent
                        }
                    }
                }
            }) {
                Text("Send")
            }
            Button(onClick = {
                if (selectedDevice.isNotEmpty()) {
                    scope.launch(Dispatchers.IO) {
                        sendPing(selectedDevice, serverPort)
                    }
                }
            }) {
                Text("Ping")
            }
            Button(onClick = {
                scope.launch(Dispatchers.IO) {
                    discoverDevices(context) { devices ->
                        scope.launch {
                            discoveredDevices = devices
                            showDropdown = true
                        }
                    }
                }
            }) {
                Text("Discover")
            }
            Button(onClick = {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                val ipAddress = wifiManager.connectionInfo.ipAddress
                val formattedIpAddress = String.format(
                    "%d.%d.%d.%d",
                    (ipAddress and 0xff),
                    (ipAddress shr 8 and 0xff),
                    (ipAddress shr 16 and 0xff),
                    (ipAddress shr 24 and 0xff)
                )
                scope.launch {
                    discoveredDevices = listOf(formattedIpAddress)
                    showDropdown = true
                }
            }) {
                Text("Show IP")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (showDropdown && discoveredDevices.isNotEmpty()) {
            LazyColumn {
                items(discoveredDevices.size) { index ->
                    TextButton(onClick = {
                        selectedDevice = discoveredDevices[index]
                        showDropdown = false
                    }) {
                        Text(discoveredDevices[index])
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(chatLog, modifier = Modifier
            .fillMaxHeight()
            .padding(16.dp))
    }

    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            startServer(serverPort) { newMessage ->
                scope.launch {
                    chatLog = "$chatLog\n$newMessage" // Update chatLog with new message
                }
            }
        }
    }
}

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

private fun startServer(port: Int, onMessageReceived: (String) -> Unit) {
    try {
        Log.d("P2PChatApp", "Starting server on port $port")
        val serverSocket = ServerSocket(port)
        while (true) {
            val clientSocket = serverSocket.accept()
            val reader = BufferedReader(InputStreamReader(clientSocket.getInputStream()))
            val message = reader.readLine()
            Log.d("P2PChatApp", "Received message: $message")
            if (message != null) {
                onMessageReceived(message)
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

private fun discoverDevices(context: Context, onDevicesDiscovered: (List<String>) -> Unit) {
    val devices = mutableListOf<String>()
    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    val multicastLock = wifiManager.createMulticastLock("multicastLock").apply { acquire() }
    try {
        DatagramSocket().use { socket ->
            socket.broadcast = true
            val sendData = "DISCOVER_REQUEST".toByteArray()
            socket.send(DatagramPacket(sendData, sendData.size, InetAddress.getByName("230.0.0.1"), 4446))
            val receiveData = ByteArray(1024)
            val receivePacket = DatagramPacket(receiveData, receiveData.size)
            socket.soTimeout = 10000 // 10-second timeout

            while (true) {
                try {
                    socket.receive(receivePacket)
                    val ip = receivePacket.address.hostAddress
                    if (ip != getLocalIpAddress() && ip !in devices) {
                        devices.add(ip)
                    }
                } catch (e: SocketTimeoutException) {
                    break // Exit the loop if timeout occurs
                }
            }
        }
    } catch (e: Exception) {
        Log.e("P2PChatApp", "Error discovering devices: ${e.message}")
    } finally {
        multicastLock.release()
        onDevicesDiscovered(devices)
    }
}

private fun getLocalIpAddress(): String {
    try {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        while (interfaces.hasMoreElements()) {
            val networkInterface = interfaces.nextElement()
            if (networkInterface.isLoopback || !networkInterface.isUp) continue

            networkInterface.interfaceAddresses.forEach { address ->
                val ip = address.address.hostAddress
                if (ip != null && !ip.contains(":")) {
                    return ip
                }
            }
        }
    } catch (e: Exception) {
        Log.e("P2PChatApp", "Error getting local IP address: ${e.message}")
    }
    return ""
}

@Preview(showBackground = true)
@Composable
fun ChatScreenPreview() {
    P2PChatAppTheme {
        ChatScreen(serverPort = 12345) // This port is just for preview purposes
    }
}
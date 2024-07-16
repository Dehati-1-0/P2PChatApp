//192.168.0.103
package com.example.p2pchatapp

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import java.net.ServerSocket
import java.net.Socket

class MainActivity : ComponentActivity() {
    private val serverPort = 12345
    private val serverIp = "10.22.127.64"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            P2PChatAppTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ChatScreen(
                        serverIp = serverIp,
                        serverPort = serverPort,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun ChatScreen(serverIp: String, serverPort: Int, modifier: Modifier = Modifier) {
    var message by remember { mutableStateOf("") }
    var chatLog by remember { mutableStateOf("Chat Log:") }
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
                if (currentMessage.isNotEmpty()) {
                    scope.launch(Dispatchers.IO) {
                        sendMessage(currentMessage, serverIp, serverPort)
                        launch(Dispatchers.Main) {
                            message = "" // Clear the message input after ensuring it's sent
                        }
                    }
                }
            }) {
                Text("Send")
            }
            Button(onClick = {
                scope.launch(Dispatchers.IO) {
                    sendPing(serverIp, serverPort)
                }
            }) {
                Text("Ping")
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

@Preview(showBackground = true)
@Composable
fun ChatScreenPreview() {
    P2PChatAppTheme {
        ChatScreen(serverIp = "127.0.0.1", serverPort = 12345) // This IP is just for preview purposes
    }
}
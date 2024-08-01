package com.example.p2pchatapp.network

import android.net.wifi.WifiManager
import android.util.Log
import com.example.p2pchatapp.model.DiscoveredDevice
import com.example.p2pchatapp.model.OfflineMessage
import com.example.p2pchatapp.data.OfflineMessagesManager.offlineMessages
import com.example.p2pchatapp.util.getDeviceModelName
import com.example.p2pchatapp.util.getLocalIpAddress
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.*

private const val BROADCAST_INTERVAL = 5000L // 5 seconds

fun sendMessage(message: String, serverIp: String, serverPort: Int, sequenceNumber: Int, callback: (String) -> Unit) {
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
                callback("delivered") // Message delivered successfully
            } else {
                callback("failed") // Message delivery failed
            }
            socket.close()
        } catch (e: Exception) {
            // Store message for offline peer
            offlineMessages.computeIfAbsent(serverIp) { mutableListOf() }
                .add(OfflineMessage("You", message, sequenceNumber))
            callback("failed") // Message delivery failed
        }
    }.start()
}

fun sendPing(serverIp: String, serverPort: Int) {
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

fun startServer(port: Int, onMessageReceived: (String, String) -> Unit, onMessageSeen: (String) -> Unit) {
    Thread {
        try {
            val serverSocket = ServerSocket(port)
            while (true) {
                val clientSocket = serverSocket.accept()
                val clientIp = clientSocket.inetAddress.hostAddress
                val reader = BufferedReader(InputStreamReader(clientSocket.getInputStream()))
                val message = reader.readLine()
                if (message != null) {
                    onMessageReceived(message, clientIp)
                    val writer = PrintWriter(clientSocket.getOutputStream(), true)
                    writer.println("ACK")
                    writer.flush()
                }
                clientSocket.close()

                // Deliver stored messages in order of their sequence number
                offlineMessages[clientIp]?.let { messages ->
                    messages.sortedBy { it.sequenceNumber }.forEach { offlineMessage ->
                        sendMessage(offlineMessage.content, clientIp, port, offlineMessage.sequenceNumber) { status ->
                            if (status == "delivered") {
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

fun listenForBroadcasts(wifiManager: WifiManager, onDeviceDiscovered: (DiscoveredDevice) -> Unit) {
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
                val parts = message.split(":")
                val ip = parts[1]
                val modelName = parts.getOrNull(2) ?: "Unknown Device"
                if (ip != localIpAddress) {
                    val device = DiscoveredDevice(ip, modelName)
                    Log.d("P2PChatApp", "Discovered device: $device")
                    onDeviceDiscovered(device)
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        Log.e("P2PChatApp", "Error listening for broadcasts: ${e.message}")
    }
}

fun broadcastIp(port: Int) {
    try {
        val broadcastAddress = InetAddress.getByName("255.255.255.255")
        val socket = DatagramSocket()
        val localIpAddress = getLocalIpAddress() ?: return
        val modelName = getDeviceModelName()
        if (modelName == "Unknown Device") return // Do not broadcast if the device model name is unknown
        val message = "DISCOVER:$localIpAddress:$modelName"
        val packet = DatagramPacket(message.toByteArray(), message.length, broadcastAddress, port)
        while (true) {
            socket.send(packet)
            Thread.sleep(BROADCAST_INTERVAL)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        Log.e("P2PChatApp", "Error broadcasting IP: ${e.message}")
    }
}
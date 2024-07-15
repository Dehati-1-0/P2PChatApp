package com.example.p2pchatapp

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.util.concurrent.Executors

class BroadcastService : Service() {
    private val multicastGroup = InetAddress.getByName("230.0.0.1")
    private val multicastPort = 4446
    private val executor = Executors.newSingleThreadExecutor()
    @Volatile private var running = true // Flag to control the broadcasting loop

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    var socket: MulticastSocket? = null
    executor.execute {
        try {
            socket = MulticastSocket(multicastPort).apply {
                joinGroup(multicastGroup)
            }
            val message = "DISCOVER:${getLocalIpAddress()}".toByteArray()

            while (running) {
                val packet = DatagramPacket(message, message.size, multicastGroup, multicastPort)
                socket?.send(packet)
                Log.d("BroadcastService", "Broadcasted discovery message")
                Thread.sleep(5000) // Broadcast every 5 seconds
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("BroadcastService", "Error broadcasting: ${e.message}")
        } finally {
            try {
                socket?.leaveGroup(multicastGroup)
                socket?.close()
            } catch (e: Exception) {
                Log.e("BroadcastService", "Error closing socket: ${e.message}")
            }
        }
    }
    return START_STICKY
}

    override fun onDestroy() {
        running = false // Stop the broadcasting loop
        executor.shutdownNow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

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
            Log.e("BroadcastService", "Error getting local IP address: ${e.message}")
        }
        return ""
    }
}
package com.example.p2pchatapp.util

import android.os.Build
import android.util.Log
import java.net.Inet4Address
import java.net.NetworkInterface

fun getLocalIpAddress(): String? {
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

fun getDeviceModelName(): String {
    return Build.MODEL ?: "Unknown"
}

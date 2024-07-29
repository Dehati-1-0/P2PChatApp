package com.example.p2pchatapp

import android.annotation.SuppressLint
import android.net.wifi.WifiManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.example.p2pchatapp.data.OfflineMessagesManager
import com.example.p2pchatapp.data.retrieveOfflineMessages
import com.example.p2pchatapp.data.storeOfflineMessages
import com.example.p2pchatapp.ui.ChatScreen
import com.example.p2pchatapp.ui.theme.P2PChatAppTheme

class MainActivity : ComponentActivity() {
    private val serverPort = 12345
    private lateinit var wifiManager: WifiManager

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        OfflineMessagesManager.offlineMessages.putAll(retrieveOfflineMessages(this))
        enableEdgeToEdge()
        setContent {
            P2PChatAppTheme {
                ChatScreen(
                    serverPort = serverPort,
                    wifiManager = wifiManager,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        storeOfflineMessages(this, OfflineMessagesManager.offlineMessages)
    }
}

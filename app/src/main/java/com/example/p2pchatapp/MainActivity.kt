package com.example.p2pchatapp

import android.annotation.SuppressLint
import android.net.wifi.WifiManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.platform.LocalContext
import com.example.p2pchatapp.data.KeyUtils
import com.example.p2pchatapp.data.OfflineMessagesManager
import com.example.p2pchatapp.data.retrieveOfflineMessages
import com.example.p2pchatapp.data.storeOfflineMessages
import com.example.p2pchatapp.ui.ChatScreen
import com.example.p2pchatapp.ui.PrivateKeyScreen
import com.example.p2pchatapp.ui.theme.P2PChatAppTheme
import java.security.KeyPair
import java.util.Base64
import android.util.Base64 as AndroidBase64

class MainActivity : ComponentActivity() {
    private val serverPort = 12345
    private lateinit var wifiManager: WifiManager

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        OfflineMessagesManager.offlineMessages.putAll(retrieveOfflineMessages(this))

        // Generate key pair if it doesn't exist
        if (!KeyUtils.keyPairExists(this)) {
            val keyPair: KeyPair = KeyUtils.generateKeyPair()
            KeyUtils.storeKeyPair(this, keyPair)
            val privateKeyString = Base64.getEncoder().encodeToString(keyPair.private.encoded)
            Toast.makeText(this, "Your private key: $privateKeyString", android.widget.Toast.LENGTH_LONG).show()
        } else {
            val privateKey = KeyUtils.getPrivateKey(this)
            val privateKeyString = Base64.getEncoder().encodeToString(privateKey?.encoded)
            Toast.makeText(this, "Your private key: $privateKeyString", android.widget.Toast.LENGTH_LONG).show()
        }

        enableEdgeToEdge()
        setContent {
            P2PChatAppTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "chat_screen") {
                    composable("chat_screen") {
                        ChatScreen(
                            serverPort = serverPort,
                            wifiManager = wifiManager,
                            modifier = Modifier.fillMaxSize(),
                            onNavigateToPrivateKey = { navController.navigate("private_key_screen") }
                        )
                    }
                    composable("private_key_screen") {
                        PrivateKeyScreen()
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        storeOfflineMessages(this, OfflineMessagesManager.offlineMessages)
    }
}

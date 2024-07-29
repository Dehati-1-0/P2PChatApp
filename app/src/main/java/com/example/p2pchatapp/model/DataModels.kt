package com.example.p2pchatapp.model

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

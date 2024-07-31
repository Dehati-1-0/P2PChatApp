package com.example.p2pchatapp.data

import android.content.Context
import android.content.SharedPreferences
import com.example.p2pchatapp.model.OfflineMessage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.concurrent.ConcurrentHashMap

private const val PREFS_NAME = "P2PChatAppPrefs"
private const val OFFLINE_MESSAGES_KEY = "OfflineMessages"

fun storeOfflineMessages(context: Context, messages: ConcurrentHashMap<String, MutableList<OfflineMessage>>) {
    val sharedPreferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val editor = sharedPreferences.edit()
    val gson = Gson()
    val json = gson.toJson(messages)
    editor.putString(OFFLINE_MESSAGES_KEY, json)
    editor.apply()
}

fun retrieveOfflineMessages(context: Context): ConcurrentHashMap<String, MutableList<OfflineMessage>> {
    val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val gson = Gson()
    val json = sharedPreferences.getString(OFFLINE_MESSAGES_KEY, null)
    val type = object : TypeToken<ConcurrentHashMap<String, MutableList<OfflineMessage>>>() {}.type
    return if (json != null) {
        gson.fromJson(json, type)
    } else {
        ConcurrentHashMap()
    }

}

    object OfflineMessagesManager {
        val offlineMessages = ConcurrentHashMap<String, MutableList<OfflineMessage>>()
    }


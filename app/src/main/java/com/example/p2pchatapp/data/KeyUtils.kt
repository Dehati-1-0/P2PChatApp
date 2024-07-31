package com.example.p2pchatapp.data

import android.content.Context
import android.content.SharedPreferences
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

object KeyUtils {
    private const val KEY_PREFS_NAME = "key_prefs"
    private const val PRIVATE_KEY_PREF = "private_key"
    private const val PUBLIC_KEY_PREF = "public_key"

    fun generateKeyPair(): KeyPair {
        val keyGen = KeyPairGenerator.getInstance("RSA")
        keyGen.initialize(2048)
        return keyGen.genKeyPair()
    }

    fun getPrivateKey(context: Context): PrivateKey? {
        val prefs = context.getSharedPreferences(KEY_PREFS_NAME, Context.MODE_PRIVATE)
        val privateKeyString = prefs.getString(PRIVATE_KEY_PREF, null) ?: return null
        val privateKeyBytes = Base64.getDecoder().decode(privateKeyString)
        return KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(privateKeyBytes))
    }

    fun getPublicKey(context: Context): PublicKey? {
        val prefs = context.getSharedPreferences(KEY_PREFS_NAME, Context.MODE_PRIVATE)
        val publicKeyString = prefs.getString(PUBLIC_KEY_PREF, null) ?: return null
        val publicKeyBytes = Base64.getDecoder().decode(publicKeyString)
        return KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(publicKeyBytes))
    }

    fun storeKeyPair(context: Context, keyPair: KeyPair) {
        val prefs = context.getSharedPreferences(KEY_PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()

        val privateKeyString = Base64.getEncoder().encodeToString(keyPair.private.encoded)
        val publicKeyString = Base64.getEncoder().encodeToString(keyPair.public.encoded)

        editor.putString(PRIVATE_KEY_PREF, privateKeyString)
        editor.putString(PUBLIC_KEY_PREF, publicKeyString)
        editor.apply()
    }

    fun keyPairExists(context: Context): Boolean {
        val prefs = context.getSharedPreferences(KEY_PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.contains(PRIVATE_KEY_PREF) && prefs.contains(PUBLIC_KEY_PREF)
    }
}

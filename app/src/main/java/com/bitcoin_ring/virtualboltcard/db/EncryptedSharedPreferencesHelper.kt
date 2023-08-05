package com.bitcoin_ring.virtualboltcard.db

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class EncryptedSharedPreferencesHelper(private val context: Context) {
    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }
    private var _encryptedSharedPreferences: SharedPreferences? = null

    val encryptedSharedPreferences: SharedPreferences
        get() {
            if (_encryptedSharedPreferences == null) {
                _encryptedSharedPreferences = EncryptedSharedPreferences.create(
                    context,
                    "VirtualBoltcardSharedPreferences",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            }
            return _encryptedSharedPreferences!!
        }

    fun storeData(context: Context, key: String, data: String) {
        val editor = _encryptedSharedPreferences!!.edit()
        editor.putString(key, data)
        editor.apply()
    }
    fun loadData(context: Context, key: String): String {
        return _encryptedSharedPreferences!!.getString(key, "") ?: ""
    }

    fun storeDataUnencrypted(context: Context, key: String, data: String) {
        val sharedPreferences = context.getSharedPreferences("HCE_PREFS", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putString(key, data)
        editor.apply()
    }
    fun loadDataUnencrypted(context: Context, key: String): String {
        val sharedPreferences = context.getSharedPreferences("HCE_PREFS", Context.MODE_PRIVATE)
        return sharedPreferences.getString(key, "") ?: ""
    }


}
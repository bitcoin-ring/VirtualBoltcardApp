package com.bitcoin_ring.virtualboltcard.db.entities
import android.content.Context
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.bitcoin_ring.virtualboltcard.db.EncryptedSharedPreferencesHelper
import com.bitcoin_ring.virtualboltcard.db.converters.AdditionalCardDataConverter
import com.bitcoin_ring.virtualboltcard.db.models.AdditionalCardData

@TypeConverters(AdditionalCardDataConverter::class)
@Entity
data class Card (
    @PrimaryKey(autoGenerate = true)
    var id: Int = 0,
    var name: String,
    var type: String,
    var uid: String,
    var url: String,
    var key1: String,
    var key2: String,
    var counter: Int,
    var drawableName: String,
    var additionalCardData: AdditionalCardData? = null
){
    fun activate(context: Context) {
        val sharedPreferencesHelper = EncryptedSharedPreferencesHelper(context)
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val sharedPreferences = EncryptedSharedPreferences.create(
            context,  // Context
            "VirtualBoltcardSharedPreferences",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        sharedPreferencesHelper.storeDataUnencrypted(context, "card_id", id.toString())
        sharedPreferencesHelper.storeDataUnencrypted(context,"counter", counter.toString())
        sharedPreferencesHelper.storeDataUnencrypted(context,"lnurltemplate",url)
        sharedPreferencesHelper.storeDataUnencrypted(context,"name",name)
        val keyEditor = sharedPreferences.edit()
        // Assuming `yourKey` is a String representation of your symmetric key
        keyEditor.putString("key1", key1)
        keyEditor.putString("key2", key2)
        keyEditor.putString("uid", uid)
        keyEditor.apply()
    }
}

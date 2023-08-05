package com.bitcoin_ring.virtualboltcard.db.converters
import android.util.Log
import androidx.room.TypeConverter
import com.bitcoin_ring.virtualboltcard.db.models.AdditionalCardData
import com.bitcoin_ring.virtualboltcard.db.models.AdditionalCardDataAdapter
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken

object AdditionalCardDataConverter {
    private val typeToken = object : TypeToken<AdditionalCardData>() {}.type

    private val gson = GsonBuilder()
        .registerTypeAdapter(typeToken, AdditionalCardDataAdapter())
        .create()

    @TypeConverter
    fun fromAdditionalCardData(additionalData: AdditionalCardData?): String? {
        additionalData?.let {
            val jsonString = gson.toJson(it, typeToken)
            Log.d("GsonConvert", "Object: $it\nJSON: $jsonString")
            return jsonString
        }
        return null
    }

    @TypeConverter
    fun toAdditionalCardData(jsonString: String?): AdditionalCardData? {
        return jsonString?.let { gson.fromJson(it, typeToken) }
    }
}

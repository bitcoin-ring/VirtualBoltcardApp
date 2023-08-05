package com.bitcoin_ring.virtualboltcard.db.models

import android.util.Log
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.annotations.Expose
import java.lang.reflect.Type

sealed class AdditionalCardData {
    abstract val type: String

    data class LNbits(
        @Expose val wallet_url: String,
        @Expose val funding_url: String,
        @Expose val apikey: String,
        @Expose val invoicekey: String
    ) : AdditionalCardData() {
        override val type: String
            get() = "LNbits"
    }}

class AdditionalCardDataAdapter : JsonSerializer<AdditionalCardData>, JsonDeserializer<AdditionalCardData> {

    override fun serialize(src: AdditionalCardData?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
        val jsonObject = JsonObject()

        when (src) {
            is AdditionalCardData.LNbits -> {
                jsonObject.addProperty("type", src.type)
                jsonObject.addProperty("wallet_url", src.wallet_url)
                jsonObject.addProperty("funding_url", src.funding_url)
                jsonObject.addProperty("apikey", src.apikey)
                jsonObject.addProperty("invoicekey", src.invoicekey)
            }
            else -> throw IllegalArgumentException("Unknown type: ${src?.javaClass?.name}")
        }

        return jsonObject
    }
    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): AdditionalCardData {
        val jsonObject = json?.asJsonObject
        val type = jsonObject?.get("type")?.asString
        Log.i("Deserialize-json", json.toString())
        return when (type) {
            "LNbits" -> AdditionalCardData.LNbits(
                jsonObject?.get("wallet_url")?.asString ?: "",
                jsonObject?.get("funding_url")?.asString ?: "",
                jsonObject?.get("apikey")?.asString ?: "",
                jsonObject?.get("invoicekey")?.asString ?: ""
            )
            else -> {
                Log.e("Deserialize-error", "No type in json data, defaulting to LNbits")
                AdditionalCardData.LNbits("", "", "", "")
            }
        }
    }
}

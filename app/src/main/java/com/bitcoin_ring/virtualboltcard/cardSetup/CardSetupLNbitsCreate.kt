package com.bitcoin_ring.virtualboltcard.cardSetup

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bitcoin_ring.virtualboltcard.MainActivity
import com.bitcoin_ring.virtualboltcard.R
import com.bitcoin_ring.virtualboltcard.db.AppDatabase
import com.bitcoin_ring.virtualboltcard.db.DatabaseUtils
import com.bitcoin_ring.virtualboltcard.db.dao.CardDao
import com.bitcoin_ring.virtualboltcard.db.entities.Card
import com.bitcoin_ring.virtualboltcard.db.models.AdditionalCardData
import com.bitcoin_ring.virtualboltcard.helper.Helper
import com.bitcoin_ring.virtualboltcard.helper.isValidUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern


class CardSetupLNbitsCreate : AppCompatActivity() {
    private lateinit var button_create: Button
    private lateinit var editLNbitsUrl: EditText
    private lateinit var editLNbitsName: EditText
    private lateinit var editLNbitsTxLimit: EditText
    private lateinit var editLNbitsDailyLimit: EditText
    private lateinit var appDatabase: AppDatabase
    private lateinit var cardDao: CardDao
    private var serverUrl = ""
    private var hintName = Helper.createRandomWord()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_card_setup_lnbits_create)
        appDatabase = DatabaseUtils.provideDatabase(this)
        cardDao = appDatabase.cardDao()
        editLNbitsUrl = findViewById<View>(R.id.editLNbitsUrl) as EditText
        editLNbitsName = findViewById<View>(R.id.editLNbitsName) as EditText
        editLNbitsName.setHint(hintName)
        editLNbitsTxLimit = findViewById<View>(R.id.editLNbitsTxLimit) as EditText
        editLNbitsDailyLimit = findViewById<View>(R.id.editLNbitsDailyLimit) as EditText
        button_create = findViewById<View>(R.id.createlnbits) as Button
        button_create.setOnClickListener {
            if (editLNbitsName.text.toString() == ""){
                editLNbitsName.setText(hintName)
            }
            if (editLNbitsTxLimit.text.toString() == ""){
                editLNbitsTxLimit.setText(R.string.default_tx_limit)
            }
            if (editLNbitsDailyLimit.text.toString() == ""){
                editLNbitsDailyLimit.setText(R.string.default_tx_limit)
            }
            if (editLNbitsUrl.text.toString() == ""){
                editLNbitsUrl.setText(R.string.lnbits_default_url)
            }
            serverUrl = editLNbitsUrl.text.toString()
            if (!serverUrl.isValidUrl()){
                return@setOnClickListener
            }
            lifecycleScope.launch(Dispatchers.IO) { // launch a new coroutine in background and continue
                    val card = JSONObject().apply {
                        put("card_name", editLNbitsName.text.toString())
                        put("uid", Helper.generateKey(7))
                        put("counter", 0)
                        put("tx_limit", editLNbitsTxLimit.text.toString())
                        put("daily_limit", editLNbitsDailyLimit.text.toString())
                        put("enable", true)
                        put("k0", Helper.generateKey(16))
                        put("k1", Helper.generateKey(16))
                        put("k2", Helper.generateKey(16))
                    }
                    val funding = JSONObject().apply {
                        put("zaps", false)
                        put("description", editLNbitsName.text.toString())
                        put("username", "vcard-" + Helper.generateKey(4))
                        put("min", 1000)
                        put("max", 100000)
                        put("comment_chars", 0)
                    }
                    Log.i("funding", funding.toString())
                        if (serverUrl.isValidUrl() == true && Helper.isNetworkAvailable(this@CardSetupLNbitsCreate)) {
                        performRequests(serverUrl, card, funding)
                    }
                }
        }
    }
    suspend fun performRequests(serverUrl: String, cardJson: JSONObject, fundingJson: JSONObject) = withContext(Dispatchers.IO) {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS) // connect timeout
            .readTimeout(10, TimeUnit.SECONDS)    // socket read timeout
            .writeTimeout(10, TimeUnit.SECONDS)   // socket write timeout
            .build()

        // Request1
        val request1 = Request.Builder()
            .url("$serverUrl/wallet?nme=vBoltCard")
            .build()

        val response1 = client.newCall(request1).execute()
        var invoiceKey = "";
        var adminKey = "";
        // Get usr and wal from the redirected URL
        var usr = ""
        var wal = ""
        var redirectUrl = ""

        if (response1.isSuccessful) {
            redirectUrl = response1.request.url.toString()
            // Get usr and wal from the redirected URL
            usr = redirectUrl.substringAfter("?usr=").substringBefore("&")
            wal = redirectUrl.substringAfter("&wal=")
            val response1Body = response1.body?.string()
            Log.i("WalletUrl", redirectUrl)
            val pattern = Pattern.compile("<strong>Admin key: <\\/strong><em>(.*?)<\\/em>")
            val matcher = pattern.matcher(response1Body)
            adminKey = if (matcher.find()) matcher.group(1) else ""
            Log.i("Adminkey", adminKey)
            val pattern2 =
                Pattern.compile("<strong>Invoice\\/read key: <\\/strong><em>(.*?)<\\/em>")
            val matcher2 = pattern2.matcher(response1Body)
            invoiceKey = if (matcher2.find()) matcher2.group(1) else ""
            Log.i("Invoicekey", invoiceKey)
        }
        if(adminKey.isEmpty()){
            //try creating an account
            val accountJson = JSONObject().apply {
                put("name", cardJson.get("card_name").toString())
            }
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = accountJson.toString().toRequestBody(mediaType)
            val request1acc = Request.Builder()
                .url("$serverUrl/api/v1/account")
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .post(requestBody)
                .build()
            val response1Acc = client.newCall(request1acc).execute()
            Log.i("response1Acc", request1acc.toString())
            // Only proceed with Request 3 if Request 2 is successful
            if (response1Acc.isSuccessful) {
                val response1AccBody = response1Acc.body?.string()
                val accountData = JSONObject(response1AccBody!!)
                usr = accountData.get("user").toString()
                wal = accountData.get("id").toString()
                adminKey = accountData.get("adminkey").toString()
                invoiceKey = accountData.get("inkey").toString()
                Log.i("AccountData",accountData.toString( ))
                redirectUrl = serverUrl + "/wallet?usr=" + usr;
            }
        }
        if (adminKey.isEmpty()) {
            return@withContext
        }
        val emptyreqbody = RequestBody.create(null, ByteArray(0))
        // Request2
        val request2 = Request.Builder()
            .url("$serverUrl/api/v1/extension/boltcards/enable?usr=$usr")
            .addHeader("Content-Type", "application/json")
            .addHeader("X-Api-Key", adminKey)
            .put(emptyreqbody)
            .build()

        val response2 = client.newCall(request2).execute()
        val response2Body = response2.body?.string()
        //Log.i("Response2", response2Body!!)
        Log.i("Response2", request2.toString())
        // Only proceed with Request 3 if Request 2 is successful
        if (response2.isSuccessful) {
            val request22 = Request.Builder()
                .url("$serverUrl/api/v1/extension/lnurlp/enable?usr=$usr")
                .addHeader("Content-Type", "application/json")
                .addHeader("X-Api-Key", adminKey)
                .put(emptyreqbody)
                .build()
            Log.i("Response22", request22.toString())

            val response22 = client.newCall(request22).execute()
            val response22Body = response22.body?.string()
            //Log.i("Response2", response2Body!!)

            // Only proceed with Request 3 if Request 2 is successful
            if (response22.isSuccessful) {
                Log.i("Response22", "successfull")
                // Request3
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val requestBody = fundingJson.toString().toRequestBody(mediaType)

                val request23 = Request.Builder()
                    .url("$serverUrl/lnurlp/api/v1/links")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "application/json")
                    .addHeader("X-Api-Key", adminKey)
                    .post(requestBody)
                    .build()

                val response23 = client.newCall(request23).execute()
                val response23Body = response23.body?.string()
                Log.i("Response23", response23Body!!)
                // Parse the response
                if (response23.isSuccessful) {
                    Log.i("Response23", "successfull")
                    val fundingdata = JSONObject(response23Body)
                    Log.i("Response23", response23Body!!)
                    // Request3
                    val mediaType = "application/json; charset=utf-8".toMediaType()
                    val requestBody = cardJson.toString().toRequestBody(mediaType)

                    val request3 = Request.Builder()
                        .url("$serverUrl/boltcards/api/v1/cards")
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Accept", "application/json")
                        .addHeader("X-Api-Key", adminKey)
                        .post(requestBody)
                        .build()

                    val response3 = client.newCall(request3).execute()
                    // Parse the response
                    if (response3.isSuccessful) {
                        Log.i("Response3", "successfull")
                        val response3Body = response3.body?.string()
                        val carddata = JSONObject(response3Body!!)
                        Log.i("Response3", response3Body!!)

                        val additionalData = AdditionalCardData.LNbits(
                            wallet_url = redirectUrl, // replace with actual data
                            funding_url = fundingdata.get("lnurl").toString(), // replace with actual data
                            apikey = adminKey, // replace with actual data
                            invoicekey = invoiceKey // replace with actual data
                        )
                        // Do something with the resultJson
                        val card = Card(
                            name = carddata.get("card_name").toString(),
                            type = "AdditionalDataLNbits",
                            uid = carddata.get("uid").toString(),
                            url = serverUrl.replace("https://","lnurlw://") + "/boltcards/api/v1/scan/" + carddata.get("external_id")
                                .toString() + "?p=<!--SUN-->&c=<!--MAC-->",
                            key1 = carddata.get("k1").toString(),
                            key2 = carddata.get("k2").toString(),
                            counter = 1,
                            drawableName = "virtualboltcard_lnbits",
                            additionalCardData = additionalData
                        )
                        val newcard_id = appDatabase.cardDao().insert(card).toInt()
                        val newcard: Card = appDatabase.cardDao().get(newcard_id)[0]
                        newcard.activate(this@CardSetupLNbitsCreate)
                        Log.i("ImportCard", newcard_id.toString())
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@CardSetupLNbitsCreate,
                                getString(R.string.finish_lnbits_create),
                                Toast.LENGTH_LONG,
                            ).show()
                            val intent = Intent(
                                this@CardSetupLNbitsCreate,
                                MainActivity::class.java
                            )
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            startActivity(intent)
                        }
                    }
                }
            }
        }
    }
}

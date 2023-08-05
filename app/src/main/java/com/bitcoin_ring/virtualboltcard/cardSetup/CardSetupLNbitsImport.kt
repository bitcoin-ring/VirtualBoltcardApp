package com.bitcoin_ring.virtualboltcard.cardSetup

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.bitcoin_ring.virtualboltcard.CardSetupManualActivity
import com.bitcoin_ring.virtualboltcard.R
import com.bitcoin_ring.virtualboltcard.db.entities.Card
import com.bitcoin_ring.virtualboltcard.db.dao.CardDao
import com.bitcoin_ring.virtualboltcard.db.AppDatabase
import com.bitcoin_ring.virtualboltcard.db.DatabaseUtils
import com.bitcoin_ring.virtualboltcard.helper.Helper
import com.bitcoin_ring.virtualboltcard.helper.isValidUrl
import com.google.gson.JsonParser
import com.google.zxing.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.dm7.barcodescanner.zxing.ZXingScannerView
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

class CardSetupLNbitsImport : AppCompatActivity(), ZXingScannerView.ResultHandler {
    private lateinit var button_import: Button
    private lateinit var editLNbitsUrl: EditText
    private lateinit var appDatabase: AppDatabase
    private lateinit var cardDao: CardDao
    private val CAMERA_REQUEST_CODE = 100
    private lateinit var scannerView: ZXingScannerView   // Programmatically initialize the scanner view

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appDatabase = DatabaseUtils.provideDatabase(this)
        cardDao = appDatabase.cardDao()
        setContentView(R.layout.activity_card_setup_lnbits_import)
        editLNbitsUrl = findViewById<View>(R.id.editLNbitsUrl) as EditText
        button_import = findViewById<View>(R.id.importlnbits) as Button
        setupScanner()
        button_import.setOnClickListener {
            val client = OkHttpClient()
            if (editLNbitsUrl.text.toString().isValidUrl() == false && !Helper.isNetworkAvailable(this)) {
                return@setOnClickListener
            }
            val request = Request.Builder().url(editLNbitsUrl.text.toString()).build()
            client.newCall(request).enqueue(object: Callback {
                override fun onResponse(call: Call, response: Response) {
                    // Now parse this JSON (see next step)
                    GlobalScope.launch(Dispatchers.IO) { // launch a new coroutine in background and continue
                        val responsestring = response.body?.string()
                        try {
                            val carddata = JsonParser.parseString(responsestring).asJsonObject
                            var card = Card(
                                name = carddata.get("card_name").asString,
                                type = "AdditionalDataLNbits",
                                uid = "00000000000000",
                                url = carddata.get("lnurlw_base").asString + "?p=<!--SUN-->&c=<!--MAC-->",
                                key1 = carddata.get("k1").asString,
                                key2 = carddata.get("k2").asString,
                                counter = 0,
                                drawableName = "virtualboltcard_lnbits"
                            )
                            val newcard_id = appDatabase.cardDao().insert(card).toInt()
                            var newcard: Card = appDatabase.cardDao().get(newcard_id)[0]
                            newcard.activate(this@CardSetupLNbitsImport)
                            Log.i("ImportCard", newcard_id.toString())
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    this@CardSetupLNbitsImport,
                                    getString(R.string.finish_import),
                                    Toast.LENGTH_LONG,
                                ).show()
                                val intent = Intent(
                                    this@CardSetupLNbitsImport,
                                    CardSetupManualActivity::class.java
                                )
                                intent.putExtra("action", R.id.cardsetup_edit)
                                intent.putExtra("card_id", newcard_id)
                                startActivity(intent)
                            }

                        } catch (e: Exception) {
                            Log.i("ImportCard", e.toString())
                            return@launch
                        }
                    }
                }

                override fun onFailure(call: Call, e: IOException) {
                    // Handle the error
                }
            })
        }
    }
    private fun setupScanner() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_REQUEST_CODE)
        } else {
            // Permission has already been granted
            startScanner()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            CAMERA_REQUEST_CODE -> {
                // If request is cancelled, the result arrays are empty.
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    // permission was granted
                    startScanner()
                } else {
                    // permission denied, you can disable the functionality that depends on this permission.
                    Toast.makeText(this, "Permission denied to camera", Toast.LENGTH_SHORT).show()
                }
                return
            }
            else -> {
                // Ignore all other requests.
            }
        }
    }

    private fun startScanner() {
        scannerView = ZXingScannerView(this)   // Programmatically initialize the scanner view
        val scannerContainer = findViewById<FrameLayout>(R.id.scanner_container)
        scannerContainer.addView(scannerView) // Add the scanner view to your layout
        scannerView.setResultHandler(this)
        scannerView.startCamera()          // Start camera on resume
    }

    override fun onPause() {
        super.onPause()
        if (::scannerView.isInitialized)
            scannerView.stopCamera()           // Stop camera on pause
    }

    public override fun onResume() {
        super.onResume()
        if (::scannerView.isInitialized) {
            scannerView.startCamera()          // Start camera on resume
            scannerView.setResultHandler(this)
        }

    }
    // Implement the handleResult function from ZXingScannerView.ResultHandler interface
    override fun handleResult(result: Result?) {
        // Do something with the scan result here.
        Log.v("tag", result!!.text) // Prints scan results
        Log.v("tag", result!!.barcodeFormat.toString()) // Prints the scan format (qrcode)
        val url = result.text
        editLNbitsUrl.setText(url)
        scannerView.stopCamera()
        val scannerContainer = findViewById<FrameLayout>(R.id.scanner_container)
        scannerContainer.removeView(scannerView)

        // If you would like to resume scanning, call this method below:
        //scannerView.resumeCameraPreview(this)
    }

}


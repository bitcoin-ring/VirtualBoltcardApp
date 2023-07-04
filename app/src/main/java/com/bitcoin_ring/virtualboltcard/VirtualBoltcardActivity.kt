package com.bitcoin_ring.virtualboltcard

import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bitcoin_ring.virtualboltcard.cardEmulation.KHostApduService
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Provider
import java.security.Security

/**
 * Created by Qifan on 28/11/2018.
 */
class VirtualBoltcardActivity : AppCompatActivity() {
    private var counter = 0
    private var mNfcAdapter: NfcAdapter? = null
    private lateinit var button: Button
    private lateinit var editText: EditText
    private lateinit var editURL: EditText
    private lateinit var editUID: EditText
    private lateinit var editCounter: EditText
    private lateinit var editK0: EditText
    private lateinit var editK1: EditText
    private lateinit var editK2: EditText
    private lateinit var editK3: EditText
    private lateinit var editK4: EditText
    private lateinit var textView: TextView
    private lateinit var mTurnNfcDialog: AlertDialog
    private val HEX_CHARS = "0123456789ABCDEF".toCharArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.card_emulator)
        val bcproviders = BouncyCastleProvider();
        Security.addProvider(bcproviders)
        for (provider: Provider in Security.getProviders()) {
            provider.services.forEach { service ->
                Log.i(TAG, "Provider: ${provider.name}, Algorithm: ${service.algorithm}")
            }
        }
        mNfcAdapter = NfcAdapter.getDefaultAdapter(this)
        button = findViewById<View>(R.id.button) as Button
        editText = findViewById<View>(R.id.editText) as EditText
        editUID = findViewById<View>(R.id.editUID) as EditText
        editUID.setText("04B462727E7580")
        editCounter = findViewById<View>(R.id.editCounter) as EditText
        editCounter.setText("100")
        editURL = findViewById<View>(R.id.editURL) as EditText
        editURL.setText("lnurlw://lnbits.bolt-ring.com/boltcards/api/v1/scan/gerbo2pvltf22g37mau8oe?p=<!--SUN-->&c=<!--MAC-->")
        editK0 = findViewById<View>(R.id.editK0) as EditText
        editK1 = findViewById<View>(R.id.editK1) as EditText
        editK1.setText("464053b6254c2b00b1faa94105909ddd")
        editK2 = findViewById<View>(R.id.editK2) as EditText
        editK2.setText("3a8abdf70ed5d9cb61b3be07d5c70022")
        editK3 = findViewById<View>(R.id.editK3) as EditText
        editK4 = findViewById<View>(R.id.editK4) as EditText
        textView = findViewById<View>(R.id.textView) as TextView
        initNFCFunction()
    }

    private fun initNFCFunction() {
        if (supportNfcHceFeature()) {
            textView.visibility = View.GONE
            editText.visibility = View.VISIBLE
            editUID.visibility = View.VISIBLE
            editCounter.visibility = View.VISIBLE
            editURL.visibility = View.VISIBLE
            editK0.visibility = View.VISIBLE
            editK1.visibility = View.VISIBLE
            editK2.visibility = View.VISIBLE
            editK3.visibility = View.VISIBLE
            editK4.visibility = View.VISIBLE
            button.visibility = View.VISIBLE
            initService()
        } else {
            textView.visibility = View.VISIBLE
            textView.visibility = View.GONE
            editText.visibility = View.GONE
            editUID.visibility = View.GONE
            editCounter.visibility = View.GONE
            editURL.visibility = View.GONE
            editK0.visibility = View.GONE
            editK1.visibility = View.GONE
            editK2.visibility = View.GONE
            editK3.visibility = View.GONE
            editK4.visibility = View.GONE
            button.visibility = View.GONE
            editText.visibility = View.GONE
            button.visibility = View.GONE
            // Prevent phone that doesn't support NFC to trigger dialog
            if (supportNfcHceFeature()) {
                showTurnOnNfcDialog()
            }
        }
    }

    private fun supportNfcHceFeature() =
        checkNFCEnable() && packageManager.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)

    private fun initService() {
        var countertxt = loadData(this, "counter")
        editCounter.setText(countertxt)
        button.setOnClickListener {
            if (TextUtils.isEmpty(editK1.text)) {
                Toast.makeText(
                    this@VirtualBoltcardActivity,
                    getString(R.string.toast_msg),
                    Toast.LENGTH_LONG,
                ).show()
            } else {
                storeData(this,"key1",editK1.text.toString())
                storeData(this,"key2",editK2.text.toString())
                storeData(this,"uid",editUID.text.toString())
                storeData(this,"counter",editCounter.text.toString())
                storeData(this,"lnurltemplate",editURL.text.toString())


                val key1 = editK1.text.toString().hexStringToByteArray()
                val key2 = editK2.text.toString().hexStringToByteArray()
                Log.i(TAG, "key1: " + editK1.text.toString())
                Log.i(TAG, "key2: " + editK2.text.toString())
                Log.i(TAG, "key1: " + key1.toHexString())
                Log.i(TAG, "key2: " + key2.toHexString())
                //val ndefmessage = createlnurl(editUID.text.toString(), editCounter.text.toString().toInt(), key1, key2, editURL.text.toString())
                val intent = Intent(this@VirtualBoltcardActivity, KHostApduService::class.java)
                //intent.putExtra("ndefMessage", ndefmessage)
                /*intent.putExtra("key1", key1)
                intent.putExtra("key2", key2)
                intent.putExtra("uid", editUID.text.toString())
                intent.putExtra("counter", counter)
                intent.putExtra("lnurltemplate", editURL.text.toString())
                 */
                startService(intent)

            }
        }
    }

    private fun checkNFCEnable(): Boolean {
        return if (mNfcAdapter == null) {
            textView.text = getString(R.string.tv_noNfc)
            false
        } else {
            mNfcAdapter?.isEnabled == true
        }
    }

    private fun showTurnOnNfcDialog() {
        mTurnNfcDialog = MaterialAlertDialogBuilder(this, R.style.MaterialAlertDialog_rounded)
            .setTitle(getString(R.string.ad_nfcTurnOn_title))
            .setMessage(getString(R.string.ad_nfcTurnOn_message))
            .setPositiveButton(
                getString(R.string.ad_nfcTurnOn_pos),
            ) { _, _ ->
                startActivity(Intent(android.provider.Settings.ACTION_NFC_SETTINGS))
            }.setNegativeButton(getString(R.string.ad_nfcTurnOn_neg)) { _, _ ->
                onBackPressedDispatcher.onBackPressed()
            }
            .create()
        mTurnNfcDialog.show()
    }

    override fun onResume() {
        super.onResume()
        if (mNfcAdapter?.isEnabled == true) {
            textView.visibility = View.GONE
            editText.visibility = View.VISIBLE
            button.visibility = View.VISIBLE
            initService()
        }
    }

    fun String.hexStringToByteArray(): ByteArray {
        val result = ByteArray(length / 2)

        for (i in result.indices) {
            val index = i * 2
            val j = substring(index, index + 2).toInt(16)
            result[i] = j.toByte()
        }

        return result
    }

    fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }
    fun storeData(context: Context, key: String, data: String) {
        val sharedPreferences = context.getSharedPreferences("HCE_PREFS", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putString(key, data)
        editor.apply()
    }
    fun loadData(context: Context, key: String): String {
        val sharedPreferences = context.getSharedPreferences("HCE_PREFS", Context.MODE_PRIVATE)
        return sharedPreferences.getString(key, "") ?: ""
    }
}

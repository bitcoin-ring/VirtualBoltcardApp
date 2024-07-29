package com.bitcoin_ring.virtualboltcard

import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.bitcoin_ring.virtualboltcard.db.AppDatabase
import com.bitcoin_ring.virtualboltcard.db.DatabaseUtils
import com.bitcoin_ring.virtualboltcard.db.dao.CardDao
import com.bitcoin_ring.virtualboltcard.db.entities.Card
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Provider
import java.security.Security

/**
 * Created by Qifan on 28/11/2018.
 */
class CardSetupManualActivity : AppCompatActivity() {
    private var counter = 0
    private var mNfcAdapter: NfcAdapter? = null
    private lateinit var button: Button
    private lateinit var editURL: EditText
    private lateinit var editName: EditText
    private lateinit var editUID: EditText
    private lateinit var editCounter: EditText
    private lateinit var editK1: EditText
    private lateinit var editK2: EditText
    private lateinit var editCardType: Spinner
    private lateinit var textView: TextView
    private lateinit var mTurnNfcDialog: AlertDialog
    private lateinit var sharedPreferences: SharedPreferences
    private var card_id = -1
    private var action = -1
    private lateinit var appDatabase: AppDatabase
    private lateinit var cardDao: CardDao
    private lateinit var keyEditor: SharedPreferences.Editor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        card_id = intent.getIntExtra("card_id", 0) // 0 is the default value
        action = intent.getIntExtra("action", 0) // 0 is the default value
        setContentView(R.layout.activity_card_setup_manual)

        val bcproviders = BouncyCastleProvider()
        Security.addProvider(bcproviders)
        for (provider: Provider in Security.getProviders()) {
            provider.services.forEach { service ->
                Log.i(TAG, "Provider: ${provider.name}, Algorithm: ${service.algorithm}")
            }
        }
        mNfcAdapter = NfcAdapter.getDefaultAdapter(this)
        button = findViewById<View>(R.id.button) as Button
        editName = findViewById<View>(R.id.editName) as EditText
        editUID = findViewById<View>(R.id.editUID) as EditText
        editCounter = findViewById<View>(R.id.editCounter) as EditText
        editCounter.setText("0")
        editURL = findViewById<View>(R.id.editURL) as EditText
        editK1 = findViewById<View>(R.id.editK1) as EditText
        editK2 = findViewById<View>(R.id.editK2) as EditText
        textView = findViewById<View>(R.id.textView) as TextView
        editCardType = findViewById(R.id.cardType)
        ArrayAdapter.createFromResource(
            this,
            R.array.spinner_options,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            editCardType.adapter = adapter
        }
        val masterKey = MasterKey.Builder(this)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        sharedPreferences = EncryptedSharedPreferences.create(
            this,  // Context
            "VirtualBoltcardSharedPreferences",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        keyEditor = sharedPreferences.edit()
        appDatabase = DatabaseUtils.provideDatabase(this)
        cardDao = appDatabase.cardDao()
        if (action == R.id.cardsetup_create){
            editName.setText("")
            editCounter.setText("")
            editURL.setText("")
            editK1.setText("")
            editK2.setText("")
            editUID.setText("")
        }
        initForm()
        if (card_id >= 0) {
            loadCardData()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("editName", editName.text.toString())
        outState.putString("editCounter", editCounter.text.toString())
        outState.putString("editURL", editURL.text.toString())
        outState.putString("editK1", editK1.text.toString())
        outState.putString("editK2", editK2.text.toString())
        outState.putString("editUID", editUID.text.toString())
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)

        editName.setText(savedInstanceState.getString("editName"))
        editCounter.setText(savedInstanceState.getString("editCounter"))
        editURL.setText(savedInstanceState.getString("editURL"))
        editK1.setText(savedInstanceState.getString("editK1"))
        editK2.setText(savedInstanceState.getString("editK2"))
        editUID.setText(savedInstanceState.getString("editUID"))
        setSpinnerSelection(editCardType, savedInstanceState.getString("editCardType")!!);
    }

    private fun supportNfcHceFeature() =
        checkNFCEnable() && packageManager.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)


    private fun initForm() {
        if (supportNfcHceFeature()) {
            textView.visibility = View.GONE
            editName.visibility = View.VISIBLE
            editUID.visibility = View.VISIBLE
            editCounter.visibility = View.VISIBLE
            editURL.visibility = View.VISIBLE
            editK1.visibility = View.VISIBLE
            editK2.visibility = View.VISIBLE
            button.visibility = View.VISIBLE
        } else {
            textView.visibility = View.VISIBLE
            editName.visibility = View.GONE
            editUID.visibility = View.GONE
            editCounter.visibility = View.GONE
            editURL.visibility = View.GONE
            editK1.visibility = View.GONE
            editK2.visibility = View.GONE
            button.visibility = View.GONE
        }
        button.setOnClickListener {
            if ((TextUtils.isEmpty(editK1.text))
                || (TextUtils.isEmpty(editK2.text))
                || (TextUtils.isEmpty(editUID.text))
                || (TextUtils.isEmpty(editURL.text))
                || (TextUtils.isEmpty(editCounter.text))
            ) {
                Toast.makeText(
                    this@CardSetupManualActivity,
                    getString(R.string.enter_valid_data),
                    Toast.LENGTH_LONG,
                ).show()
            } else {
                //storeData(this,"key1",editK1.text.toString())
                //storeData(this,"key2",editK2.text.toString())
                //storeData(this,"uid",editUID.text.toString())
                storeData(this,"name",editName.text.toString())
                storeData(this,"counter",editCounter.text.toString())
                storeData(this,"lnurltemplate",editURL.text.toString())

                // Assuming `yourKey` is a String representation of your symmetric key
                keyEditor.putString("key1", editK1.text.toString())
                keyEditor.putString("key2", editK2.text.toString())
                keyEditor.putString("uid", editUID.text.toString())
                keyEditor.apply()

                val key1 = editK1.text.toString().hexStringToByteArray()
                val key2 = editK2.text.toString().hexStringToByteArray()
                Log.i(TAG, "key1: " + editK1.text.toString())
                Log.i(TAG, "key2: " + editK2.text.toString())
                Log.i(TAG, "key1: " + key1.toHexString())
                Log.i(TAG, "key2: " + key2.toHexString())
                var carddrawable = "virtualboltcard"
                if (editCardType.selectedItem.toString() == "AdditionalDataLNbits"){
                    carddrawable = "virtualboltcard_lnbits"
                }

                var card = Card(
                    name = editName.text.toString(),
                    type = "Manual",
                    uid = editUID.text.toString(),
                    url = editURL.text.toString(),
                    key1 = editK1.text.toString(),
                    key2 = editK2.text.toString(),
                    counter = 0,
                    drawableName = "virtualboltcard"
                )
                if(action == R.id.cardsetup_edit && card_id > 0) {
                    card = Card(
                        id = card_id,
                        name = editName.text.toString(),
                        type = editCardType.selectedItem.toString(),
                        uid = editUID.text.toString(),
                        url = editURL.text.toString(),
                        key1 = editK1.text.toString(),
                        key2 = editK2.text.toString(),
                        counter = editCounter.text.toString().toInt(),
                        drawableName = carddrawable,
                    )
                }
                // Now, insert the card into the database
                lifecycleScope.launch(Dispatchers.IO) { // launch a new coroutine in background and continue
                    var set_active_card_id = 0
                    if(action == R.id.cardsetup_create) {
                        set_active_card_id = appDatabase.cardDao().insert(card).toInt()
                    }
                    if(action == R.id.cardsetup_edit) {
                        appDatabase.cardDao().updateCard(
                            card.id,
                            editName.text.toString(),
                            editUID.text.toString(),
                            editURL.text.toString(),
                            editK1.text.toString(),
                            editK2.text.toString(),
                            editCounter.text.toString().toInt(),
                            carddrawable
                        )
                        set_active_card_id = card.id
                    }
                    val active_card: Card = appDatabase.cardDao().get(set_active_card_id)[0]
                    active_card.activate(this@CardSetupManualActivity)

                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@CardSetupManualActivity,
                            getString(R.string.card_saved),
                            Toast.LENGTH_LONG,
                        ).show()
                        // Set the adapter in the main thread
                        //finish()
                        val intent = Intent(this@CardSetupManualActivity, MainActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        startActivity(intent)
                    }
                }
            }
        }
    }


    private fun loadCardData() {
        lifecycleScope.launch(Dispatchers.IO) { // launch a new coroutine in background and continue
            val card: Card = appDatabase.cardDao().get(card_id).first()
            withContext(Dispatchers.Main) {
                val cardtype = card.type;
                setSpinnerSelection(editCardType, cardtype);

                val name = card.name
                if (name.length > 0) {
                    editName.setText(name)
                }
                val countertxt = card.counter.toString()
                if (countertxt.length > 0) {
                    editCounter.setText(countertxt)
                }
                val lnurltemplate = card.url
                if (lnurltemplate.length > 0) {
                    editURL.setText(lnurltemplate)
                }
                val key1 = card.key1
                if (key1.length > 0) {
                    editK1.setText(key1)
                }
                val key2 = card.key2
                if (key2.length > 0) {
                    editK2.setText(key2)
                }
                val uid = card.uid
                if (uid.length > 0) {
                    editUID.setText(uid)
                }
                if (action == R.id.cardsetup_create) {
                    editName.setText("")
                    editCounter.setText("")
                    editURL.setText("")
                    editK1.setText("")
                    editK2.setText("")
                    editUID.setText("")
                }
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


    override fun onResume() {
        super.onResume()
        if (mNfcAdapter?.isEnabled == true) {
            textView.visibility = View.GONE
            button.visibility = View.VISIBLE
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

    private fun setSpinnerSelection(spinner: Spinner, value: String) {
        val adapter = spinner.adapter as ArrayAdapter<String>
        val position = adapter.getPosition(value)
        if (position >= 0) {
            Log.i(TAG, "Selected CardType: " + value)
            spinner.setSelection(position)
        } else {
            Toast.makeText(this, "Value not found in Spinner", Toast.LENGTH_SHORT).show()
        }
    }

}

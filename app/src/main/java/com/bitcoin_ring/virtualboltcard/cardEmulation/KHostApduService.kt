package com.bitcoin_ring.virtualboltcard.cardEmulation

import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.bitcoin_ring.virtualboltcard.db.AppDatabase
import com.bitcoin_ring.virtualboltcard.db.DatabaseUtils
import com.bitcoin_ring.virtualboltcard.db.dao.CardDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.UnsupportedEncodingException
import java.math.BigInteger
import java.security.Security
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Created by Qifan on 05/12/2018.
 */

class KHostApduService : HostApduService() {

    private val TAG = "HostApduService"

    private val APDU_SELECT = byteArrayOf(
        0x00.toByte(), // CLA	- Class - Class of instruction
        0xA4.toByte(), // INS	- Instruction - Instruction code
        0x04.toByte(), // P1	- Parameter 1 - Instruction parameter 1
        0x00.toByte(), // P2	- Parameter 2 - Instruction parameter 2
        0x07.toByte(), // Lc field	- Number of bytes present in the data field of the command
        0xD2.toByte(),
        0x76.toByte(),
        0x00.toByte(),
        0x00.toByte(),
        0x85.toByte(),
        0x01.toByte(),
        0x01.toByte(), // NDEF Tag Application name
        0x00.toByte(), // Le field	- Maximum number of bytes expected in the data field of the response to the command
    )

    private val CAPABILITY_CONTAINER_OK = byteArrayOf(
        0x00.toByte(), // CLA	- Class - Class of instruction
        0xa4.toByte(), // INS	- Instruction - Instruction code
        0x00.toByte(), // P1	- Parameter 1 - Instruction parameter 1
        0x0c.toByte(), // P2	- Parameter 2 - Instruction parameter 2
        0x02.toByte(), // Lc field	- Number of bytes present in the data field of the command
        0xe1.toByte(),
        0x03.toByte(), // file identifier of the CC file
    )

    private val READ_CAPABILITY_CONTAINER = byteArrayOf(
        0x00.toByte(), // CLA	- Class - Class of instruction
        0xb0.toByte(), // INS	- Instruction - Instruction code
        0x00.toByte(), // P1	- Parameter 1 - Instruction parameter 1
        0x00.toByte(), // P2	- Parameter 2 - Instruction parameter 2
        0x0f.toByte(), // Lc field	- Number of bytes present in the data field of the command
    )

    // In the scenario that we have done a CC read, the same byte[] match
    // for ReadBinary would trigger and we don't want that in succession
    private var READ_CAPABILITY_CONTAINER_CHECK = false

    private val READ_CAPABILITY_CONTAINER_RESPONSE_ORIG = byteArrayOf(
        0x00.toByte(), 0x11.toByte(), // CCLEN length of the CC file
        0x20.toByte(), // Mapping Version 2.0
        0xFF.toByte(), 0xFF.toByte(), // MLe maximum
        0xFF.toByte(), 0xFF.toByte(), // MLc maximum
        0x04.toByte(), // T field of the NDEF File Control TLV
        0x06.toByte(), // L field of the NDEF File Control TLV
        0xE1.toByte(), 0x04.toByte(), // File Identifier of NDEF file
        0xFF.toByte(), 0xFE.toByte(), // Maximum NDEF file size of 65534 bytes
        0x00.toByte(), // Read access without any security
        0xFF.toByte(), // Write access without any security
        0x90.toByte(), 0x00.toByte(), // A_OKAY
    )
    private val READ_CAPABILITY_CONTAINER_RESPONSE = byteArrayOf(
        0x00.toByte(),
        0x17.toByte(),
        0x20.toByte(),
        0x01.toByte(),
        0x00.toByte(),
        0x00.toByte(),
        0xFF.toByte(),
        0x04.toByte(),
        0x06.toByte(),
        0xE1.toByte(),
        0x04.toByte(),
        0x01.toByte(),
        0x00.toByte(),
        0x00.toByte(),
        0xFF.toByte(),
        0x90.toByte(), 0x00.toByte(), // A_OKAY
    )

    private val NDEF_SELECT_OK = byteArrayOf(
        0x00.toByte(), // CLA	- Class - Class of instruction
        0xa4.toByte(), // Instruction byte (INS) for Select command
        0x00.toByte(), // Parameter byte (P1), select by identifier
        0x0c.toByte(), // Parameter byte (P1), select by identifier
        0x02.toByte(), // Lc field	- Number of bytes present in the data field of the command
        0xE1.toByte(),
        0x04.toByte(), // file identifier of the NDEF file retrieved from the CC file
    )

    private val NDEF_READ_BINARY = byteArrayOf(
        0x00.toByte(), // Class byte (CLA)
        0xb0.toByte(), // Instruction byte (INS) for ReadBinary command
    )

    private val NDEF_READ_BINARY_NLEN = byteArrayOf(
        0x00.toByte(), // Class byte (CLA)
        0xb0.toByte(), // Instruction byte (INS) for ReadBinary command
        0x00.toByte(),
        0x00.toByte(), // Parameter byte (P1, P2), offset inside the CC file
        0x02.toByte(), // Le field
    )

    private val A_OKAY = byteArrayOf(
        0x90.toByte(), // SW1	Status byte 1 - Command processing status
        0x00.toByte(), // SW2	Status byte 2 - Command processing qualifier
    )

    private val A_ERROR = byteArrayOf(
        0x6A.toByte(), // SW1	Status byte 1 - Command processing status
        0x82.toByte(), // SW2	Status byte 2 - Command processing qualifier
    )

    private val NDEF_ID = byteArrayOf(0xE1.toByte(), 0x04.toByte())

    private var NDEF_URI = NdefMessage(createTextRecord("en", "virtual boltcard initializing", NDEF_ID))
    private var NDEF_URI_BYTES = NDEF_URI.toByteArray()
    private var NDEF_URI_LEN = fillByteArrayToFixedDimension(
        BigInteger.valueOf(NDEF_URI_BYTES.size.toLong()).toByteArray(),
        2,
    )
    private var key1 = ByteArray(16)
    private var key2 = ByteArray(16)
    private var uid = ""
    private var counter = 0
    private var lnurltemplate = ""
    private lateinit var appDatabase: AppDatabase
    private lateinit var cardDao: CardDao
    private var lastSuccessfulScanTime: Long = 0
    override fun onCreate() {
        super.onCreate()
        appDatabase = DatabaseUtils.provideDatabase(this)
        cardDao = appDatabase.cardDao()
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        //return Service.START_REDELIVER_INTENT
        super.onStartCommand(intent, flags, startId)

        // Use startForeground(), not startService(), to get a foreground service.

        return Service.START_STICKY
    }

    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray {
        val currentTime = System.currentTimeMillis()

        // Check if the cooldown period has passed
        if (currentTime - lastSuccessfulScanTime < 200) {
            // The cooldown has not passed; return a specific response or null
            Log.wtf(TAG, "processCommandApdu() | still in cooldown for " + (currentTime - lastSuccessfulScanTime) + "ms")
            return A_ERROR
        }

        //
        // The following flow is based on Appendix E "Example of Mapping Version 2.0 Command Flow"
        // in the NFC Forum specification
        //
        Log.i(TAG, "processCommandApdu() | incoming commandApdu: " + commandApdu.toHex())

        //
        // First command: NDEF Tag Application select (Section 5.5.2 in NFC Forum spec)
        //
        if (APDU_SELECT.contentEquals(commandApdu)) {
            Log.i(TAG, "APDU_SELECT triggered. Our Response: " + A_OKAY.toHex())
            return A_OKAY
        }

        //
        // Second command: Capability Container select (Section 5.5.3 in NFC Forum spec)
        //
        if (CAPABILITY_CONTAINER_OK.contentEquals(commandApdu)) {
            Log.i(TAG, "CAPABILITY_CONTAINER_OK triggered. Our Response: " + A_OKAY.toHex())
            return A_OKAY
        }

        //
        // Third command: ReadBinary data from CC file (Section 5.5.4 in NFC Forum spec)
        //
        if (READ_CAPABILITY_CONTAINER.contentEquals(commandApdu) && !READ_CAPABILITY_CONTAINER_CHECK
        ) {
            Log.i(
                TAG,
                "READ_CAPABILITY_CONTAINER triggered. Our Response: " + READ_CAPABILITY_CONTAINER_RESPONSE.toHex(),
            )
            READ_CAPABILITY_CONTAINER_CHECK = true
            return READ_CAPABILITY_CONTAINER_RESPONSE
        }

        //
        // Fourth command: NDEF Select command (Section 5.5.5 in NFC Forum spec)
        //
        if (NDEF_SELECT_OK.contentEquals(commandApdu)) {
            Log.i(TAG, "NDEF_SELECT_OK triggered. Our Response: " + A_OKAY.toHex())
            return A_OKAY
        }

        if (NDEF_READ_BINARY_NLEN.contentEquals(commandApdu)) {
            // Build our response
            val lnurl = createlnurl()
            Log.i(TAG, "NDEF_READ_BINARY_NLEN triggered. lnurl: " + lnurl)
            val uri = Uri.parse(lnurl)
            NDEF_URI = NdefMessage(NdefRecord.createUri(uri))

            NDEF_URI_BYTES = NDEF_URI.toByteArray()
            NDEF_URI_LEN = fillByteArrayToFixedDimension(
                BigInteger.valueOf(NDEF_URI_BYTES.size.toLong()).toByteArray(),
                2,
            )

            val response = ByteArray(NDEF_URI_LEN.size + A_OKAY.size)
            System.arraycopy(NDEF_URI_LEN, 0, response, 0, NDEF_URI_LEN.size)
            System.arraycopy(A_OKAY, 0, response, NDEF_URI_LEN.size, A_OKAY.size)

            Log.i(TAG, "NDEF_READ_BINARY_NLEN triggered. Our Response: " + response.toHex())

            READ_CAPABILITY_CONTAINER_CHECK = false
            return response
        }

        if (commandApdu.size >= 2 && commandApdu.sliceArray(0..1).contentEquals(NDEF_READ_BINARY)) {
            val lnurl = createlnurl()
            Log.i(TAG, "NDEF_READ_BINARY triggered. lnurl: " + lnurl)

            val uri = Uri.parse(lnurl)
            NDEF_URI = NdefMessage(NdefRecord.createUri(uri))
            //NDEF_URI = NdefMessage(createTextRecord("en", lnurl, NDEF_ID))

            NDEF_URI_BYTES = NDEF_URI.toByteArray()
            NDEF_URI_LEN = fillByteArrayToFixedDimension(
                BigInteger.valueOf(NDEF_URI_BYTES.size.toLong()).toByteArray(),
                2,
            )
            val offset = commandApdu.sliceArray(2..3).toHex().toInt(16)
            val length = commandApdu.sliceArray(4..4).toHex().toInt(16)

            val fullResponse = ByteArray(NDEF_URI_LEN.size + NDEF_URI_BYTES.size)
            System.arraycopy(NDEF_URI_LEN, 0, fullResponse, 0, NDEF_URI_LEN.size)
            System.arraycopy(
                NDEF_URI_BYTES,
                0,
                fullResponse,
                NDEF_URI_LEN.size,
                NDEF_URI_BYTES.size,
            )
            counter++
            storeData(this, "counter", counter.toString())
            val card_id = loadData(this@KHostApduService,"card_id").toInt()
            CoroutineScope(Dispatchers.IO).launch{
                cardDao.incCounter(card_id = card_id)
            }
            storeData(this, "counter", counter.toString())

            Log.i(TAG, "NDEF_READ_BINARY triggered. Full data: " + fullResponse.toHex())
            Log.i(TAG, "READ_BINARY - OFFSET: $offset - LEN: $length")

            val slicedResponse = fullResponse.sliceArray(offset until fullResponse.size)

            // Build our response
            val realLength = if (slicedResponse.size <= length) slicedResponse.size else length
            val response = ByteArray(realLength + A_OKAY.size)

            System.arraycopy(slicedResponse, 0, response, 0, realLength)
            System.arraycopy(A_OKAY, 0, response, realLength, A_OKAY.size)

            Log.i(TAG, "NDEF_READ_BINARY triggered. Our Response: " + response.toHex())

            READ_CAPABILITY_CONTAINER_CHECK = false
            /*GlobalScope.launch(Dispatchers.IO) { // launch a new coroutine in background and continue
                withContext(Dispatchers.Main) {
                };
            };*/
            /*if (Settings.canDrawOverlays(this)) {
                notifyScanOverdraw()
            }*/
            notifyScan()
            lastSuccessfulScanTime = currentTime
            return response
        }

        //
        // We're doing something outside our scope
        //
        Log.wtf(TAG, "processCommandApdu() | I don't know what's going on!!!")
        return A_ERROR
    }

    override fun onDeactivated(reason: Int) {
        Log.i(TAG, "onDeactivated() Fired!")
    }

    private val HEX_CHARS = "0123456789ABCDEF".toCharArray()

    private fun ByteArray.toHex(): String {
        val result = StringBuffer()

        forEach {
            val octet = it.toInt()
            val firstIndex = (octet and 0xF0).ushr(4)
            val secondIndex = octet and 0x0F
            result.append(HEX_CHARS[firstIndex])
            result.append(HEX_CHARS[secondIndex])
        }

        return result.toString()
    }

    private fun createTextRecord(language: String, text: String, id: ByteArray): NdefRecord {
        val languageBytes: ByteArray
        val textBytes: ByteArray
        try {
            languageBytes = language.toByteArray(charset("US-ASCII"))
            textBytes = text.toByteArray(charset("UTF-8"))
        } catch (e: UnsupportedEncodingException) {
            throw AssertionError(e)
        }

        val recordPayload = ByteArray(1 + (languageBytes.size and 0x03F) + textBytes.size)

        recordPayload[0] = (languageBytes.size and 0x03F).toByte()
        System.arraycopy(languageBytes, 0, recordPayload, 1, languageBytes.size and 0x03F)
        System.arraycopy(
            textBytes,
            0,
            recordPayload,
            1 + (languageBytes.size and 0x03F),
            textBytes.size,
        )

        return NdefRecord(NdefRecord.TNF_WELL_KNOWN, NdefRecord.RTD_TEXT, id, recordPayload)
    }

    private fun fillByteArrayToFixedDimension(array: ByteArray, fixedSize: Int): ByteArray {
        if (array.size == fixedSize) {
            return array
        }
        val start = byteArrayOf(0x00.toByte())
        val filledArray = ByteArray(start.size + array.size)
        System.arraycopy(start, 0, filledArray, 0, start.size)
        System.arraycopy(array, 0, filledArray, start.size, array.size)
        return fillByteArrayToFixedDimension(filledArray, fixedSize)
    }


    fun createlnurl() : String {
        val masterKey = MasterKey.Builder(this)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val sharedPreferences = EncryptedSharedPreferences.create(
            this,  // Context
            "VirtualBoltcardSharedPreferences",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        key1 = sharedPreferences.getString("key1", null)!!.hexStringToByteArray()
        key2 = sharedPreferences.getString("key2", null)!!.hexStringToByteArray()
        uid = sharedPreferences.getString("uid", null)!!
        counter = loadData(this,"counter").toInt()
        lnurltemplate =loadData(this,"lnurltemplate")
        Log.i(TAG, "key1 | received prefs: " + key1.toHex())
        Log.i(TAG, "key2 | received prefs: " + key2.toHex())
        Log.i(TAG, "uid | received prefs: " + uid)
        Log.i(TAG, "counter | received prefs: " + counter)
        Log.i(TAG, "lnurltemplate | received prefs: " + lnurltemplate)
        Log.i(TAG, "onStartCommand() | NDEF$NDEF_URI")
        val counter_ba = intToThreeBytesLittleEndian(counter)
        Log.i(ContentValues.TAG, "urlTemplate: " + lnurltemplate)
        Log.i(ContentValues.TAG, "counter: " + counter_ba.toHexString())
        val uid_ba = uid.hexStringToByteArray()
        Log.i(ContentValues.TAG, "uid: " + uid_ba.toHexString())
        val encrypted = encryptSUN(uid_ba, counter_ba, key1)
        Log.i(ContentValues.TAG, "encrypted: " + encrypted.toHexString())
        val mac = getSunMAC(uid_ba, counter_ba, key2)
        Log.i(ContentValues.TAG, "mac: " + mac.toHexString())
        val ndefmessage = lnurltemplate.replace("<!--SUN-->",encrypted.toHexString()).replace("<!--MAC-->",mac.toHexString())
        return ndefmessage
    }

    fun getSunMAC(UID: ByteArray, counter: ByteArray, key: ByteArray): ByteArray {
        val sv2prefix = "3CC300010080".hexStringToByteArray()
        //val sv2bytes = sv2prefix + UID + counter
        val sv2bytes = ByteArray(16)
        sv2bytes[0] = 0x80.toByte()
        sv2prefix.copyInto(sv2bytes, 0)
        UID.copyInto(sv2bytes, 6)
        counter.copyInto(sv2bytes, 13)
        Log.i(ContentValues.TAG, "sv2bytes: " + sv2bytes.toHexString())

        val mac1 = myCMAC(key, sv2bytes)
        val mac2 = myCMAC(mac1, "".toByteArray())

        return mac2.indices.filter { it % 2 != 0 }.map { mac2[it] }.toByteArray()
    }

    fun myCMAC(key: ByteArray, data: ByteArray): ByteArray {
        Log.i(ContentValues.TAG, "CMAC-key: " + key.toHexString())
        Log.i(ContentValues.TAG, "CMAC-data: " + data.toHexString())
        val mac = Mac.getInstance("AESCMAC", "AndroidOpenSSL")
        val secretKey = SecretKeySpec(key, "AES")
        mac.init(secretKey)
        return mac.doFinal(data)
    }

    fun encryptSUN(UID: ByteArray, counter: ByteArray, key: ByteArray): ByteArray {
        Security.addProvider(BouncyCastleProvider())

        val IVbytes = ByteArray(16) // A byte array of 16 zeros

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding", BouncyCastleProvider.PROVIDER_NAME)
        val secretKeySpec = SecretKeySpec(key, "AES")
        val ivParameterSpec = IvParameterSpec(IVbytes)

        // Prepare plaintext by combining UID and counter
        val sunPlain = ByteArray(16)
        sunPlain[0] = 0x80.toByte()
        UID.copyInto(sunPlain, 1)
        counter.copyInto(sunPlain, 8)

        // Initialize cipher for encryption
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivParameterSpec)

        // Encrypt plaintext to get sun
        val sun = cipher.doFinal(sunPlain)

        return sun
    }

    fun intToThreeBytesLittleEndian(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte()
        )
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

    fun String.hexStringToByteArray(): ByteArray {
        val result = ByteArray(length / 2)

        for (i in result.indices) {
            val index = i * 2
            val j = substring(index, index + 2).toInt(16)
            result[i] = j.toByte()
        }
        return result
    }

    fun notifyScan(){
        val intent = Intent(this@KHostApduService, ScanSuccessful::class.java).apply {
            // You can put extra data into the Intent if needed with putExtra
            // intent.putExtra("key", "value")

            // This flag is needed to start an activity from a context outside of an activity
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or Intent.FLAG_ACTIVITY_NO_HISTORY
        }
        startActivity(intent)
    }
}

package com.bitcoin_ring.virtualboltcard.fragments

import android.animation.ObjectAnimator
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bitcoin_ring.virtualboltcard.R
import com.bitcoin_ring.virtualboltcard.db.AppDatabase
import com.bitcoin_ring.virtualboltcard.db.DatabaseUtils
import com.bitcoin_ring.virtualboltcard.db.dao.CardDao
import com.bitcoin_ring.virtualboltcard.db.entities.Card
import com.bitcoin_ring.virtualboltcard.db.models.AdditionalCardData
import com.bitcoin_ring.virtualboltcard.helper.Helper
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.journeyapps.barcodescanner.BarcodeEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.net.URL
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

private lateinit var appDatabase: AppDatabase
private lateinit var cardDao: CardDao

class AdditionalDataLNbitsFragment : Fragment() {
    private var cardId:Int = 0
    private lateinit var appDatabase: AppDatabase
    private lateinit var cardDao: CardDao

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_additional_data_lnbits, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val spinner: ProgressBar = view.findViewById(R.id.loadingSpinner)
        val contentLayout: View = view.findViewById(R.id.contentLayout)
        spinner.visibility = View.VISIBLE
        contentLayout.visibility = View.GONE
        val context: Context = requireContext()
        appDatabase = DatabaseUtils.provideDatabase(context)
        cardId = arguments?.getInt("cardId") ?: -1
        val job = lifecycleScope.launch(Dispatchers.IO) { // launch a new coroutine in background and continue
            val card: Card = appDatabase.cardDao().get(cardId)[0]
            val additionalData = card.additionalCardData
            val invoiceKey: String? = if (additionalData is AdditionalCardData.LNbits) {
                additionalData.invoicekey
            } else {
                null
            }
            val wallet_url: String? = if (additionalData is AdditionalCardData.LNbits) {
                additionalData.wallet_url
            } else {
                null
            }
            val funding_url: String? = if (additionalData is AdditionalCardData.LNbits) {
                additionalData.funding_url
            } else {
                null
            }
            var fundingBitmap: Bitmap? = null
            val multiFormatWriter = MultiFormatWriter()
            if (funding_url?.isNotEmpty() == true && funding_url?.equals("n/a") == false) {
                val fundingBitMatrix =
                    multiFormatWriter.encode(funding_url, BarcodeFormat.QR_CODE, 500, 500)
                val fundingBarcodeEncoder = BarcodeEncoder()
                fundingBitmap = fundingBarcodeEncoder.createBitmap(fundingBitMatrix)
            }
            withContext(Dispatchers.Main) {
                if (wallet_url?.isNotEmpty() == true && wallet_url?.equals("n/a") == false) {
                    Log.i("wallet",wallet_url)
                    val walletText = view.findViewById<TextView>(R.id.wallet_qr_code_label)
                    walletText.setOnClickListener {
                        openUrlInBrowser(wallet_url)
                    }
                    walletText.visibility = View.VISIBLE;
                }
                if (funding_url?.isNotEmpty() == true && funding_url?.equals("n/a") == false) {
                    Log.i("funding",funding_url)
                    val fundingQrCode = view.findViewById<ImageView>(R.id.funding_qr_code)
                    fundingQrCode.setImageBitmap(fundingBitmap)
                    fundingQrCode.setOnClickListener {
                        openInWallet(funding_url)
                    }
                    val fundingText = view.findViewById<TextView>(R.id.funding_qr_code_label)
                    fundingText.setOnClickListener {
                        openInWallet(funding_url)
                    }
                    fundingQrCode.visibility = View.VISIBLE;
                    fundingText.visibility = View.VISIBLE;
                }
                val context = requireContext()
                if (wallet_url.toString().isValidUrl() == false || !Helper.isNetworkAvailable(context)){
                    spinner.visibility = View.GONE
                    contentLayout.visibility = View.VISIBLE
                    val fadeIn = ObjectAnimator.ofFloat(contentLayout, "alpha", 0f, 1f)
                    fadeIn.duration = 500 // duration in milliseconds
                    fadeIn.start()
                    this@launch.cancel()
                }
            }
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS) // connect timeout
                .readTimeout(10, TimeUnit.SECONDS)    // socket read timeout
                .writeTimeout(10, TimeUnit.SECONDS)   // socket write timeout
                .build()
            val url = URL(wallet_url)
            val apiurl = url.protocol + "://" + url.host;
            Log.i("BalanceUrl", "$apiurl/api/v1/wallet")
            val requestbalance = Request.Builder()
                .url("$apiurl/api/v1/wallet")
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .addHeader("X-Api-Key", invoiceKey.toString())
                .get()
                .build()
            lateinit var responsebalance : Response;
            try {
                responsebalance = client.newCall(requestbalance).execute()
            }
            catch (e: Exception){
                return@launch
            }
            val response = responsebalance.body?.string()
            // Parse the response
            var balanceint = 0
            if (responsebalance.isSuccessful) {
                val balancedata = JSONObject(response)
                try {
                    balanceint = balancedata.get("balance").toString().toInt()
                }
                catch (e:Exception){
                    Log.i("LNbitsBalance", "invalid balance: $balancedata")
                    return@launch
                }
                withContext(Dispatchers.Main) {
                    val balancetext = (balanceint / 1000).toString() + " Sats"
                    val balanceTextView = view.findViewById<TextView>(R.id.balance)
                    balanceTextView.text = balancetext
                    balanceTextView.visibility = View.VISIBLE;
                }
            }
            withContext(Dispatchers.Main) {
                spinner.visibility = View.GONE
                contentLayout.visibility = View.VISIBLE
                val fadeIn = ObjectAnimator.ofFloat(contentLayout, "alpha", 0f, 1f)
                fadeIn.duration = 500 // duration in milliseconds
                fadeIn.start()
            }
        }
    }

    private fun openUrlInBrowser(url: String) {
        try {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(browserIntent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "Please install an app to handel URLs (..like a browser?).", Toast.LENGTH_LONG).show()
        }
    }

    private fun openInWallet(url: String) {
        try {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("lightning:" + url))
            startActivity(browserIntent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "Please install an app that can can handle LNURL-links", Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        fun newInstance(cardId: Int): AdditionalDataLNbitsFragment {
            val fragment = AdditionalDataLNbitsFragment()
            val args = Bundle()
            args.putInt("cardId", cardId)
            fragment.arguments = args
            return fragment
        }
    }
}

fun String.isValidUrl(): Boolean {
    val p = Pattern.compile(
        "^(https?:\\/\\/)" +        // scheme
                "((([a-z\\d]([a-z\\d-]*[a-z\\d])*)\\.)+[a-z]{2,}|" +  // domain name and extension
                "((\\d{1,3}\\.){3}\\d{1,3}))" +  // OR ip (v4) address
                "(\\:\\d+)?" +     // port
                "(\\/[-a-z\\d%_.~+]*)*" +   // path
                "(\\?[;&a-z\\d%_.~+=-]*)?" +   // query string
                "(\\#[-a-z\\d_]*)?$", // fragment locator
        Pattern.CASE_INSENSITIVE
    )
    val m = p.matcher(this)
    return m.matches()
}
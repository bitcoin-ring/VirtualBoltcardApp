package com.bitcoin_ring.virtualboltcard

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.nfc.NfcAdapter
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bitcoin_ring.virtualboltcard.cardEmulation.KHostApduService
import com.bitcoin_ring.virtualboltcard.cardSetup.CardSetupLNbitsCreate
import com.bitcoin_ring.virtualboltcard.cardSetup.CardSetupLNbitsImport
import com.bitcoin_ring.virtualboltcard.cardSetup.NoCardYetActivity
import com.bitcoin_ring.virtualboltcard.db.AppDatabase
import com.bitcoin_ring.virtualboltcard.db.DatabaseUtils.provideDatabase
import com.bitcoin_ring.virtualboltcard.db.EncryptedSharedPreferencesHelper
import com.bitcoin_ring.virtualboltcard.db.dao.CardDao
import com.bitcoin_ring.virtualboltcard.db.entities.Card
import com.bitcoin_ring.virtualboltcard.fragments.AdditionalDataLNbitsFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private var mNfcAdapter: NfcAdapter? = null
    private lateinit var mTurnNfcDialog: AlertDialog
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var viewPager: ViewPager2
    private lateinit var pageChangeCallback: ViewPager2.OnPageChangeCallback
    private lateinit var appDatabase: AppDatabase
    private lateinit var cardDao: CardDao
    private lateinit var sharedPreferencesHelper: EncryptedSharedPreferencesHelper
    private var currentCard: Card? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedPreferencesHelper = EncryptedSharedPreferencesHelper(this)
        sharedPreferences = sharedPreferencesHelper.encryptedSharedPreferences
        //val button = findViewById<Button>(R.id.button_launcheditcarddata)
        appDatabase = provideDatabase(this)
        cardDao = appDatabase.cardDao()
        setContentView(R.layout.activity_main)
        viewPager = findViewById(R.id.viewPager)
        val fab: FloatingActionButton = findViewById(R.id.fab)

        fab.setOnClickListener { view ->
            Log.d("FAB", "FAB Clicked!")
            val popupMenu = PopupMenu(this, view)
            val card = currentCard
            popupMenu.menuInflater.inflate(R.menu.fab_menu, popupMenu.menu)
            popupMenu.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action1 -> {
                        // handle action1
                        val intent = Intent(this, CardSetupLNbitsCreate::class.java)
                        startActivity(intent)
                        true
                    }
                    R.id.action2 -> {
                        // handle action2
                        val intent = Intent(this, CardSetupLNbitsImport::class.java)
                        startActivity(intent)
                        true
                    }
                    R.id.action3 -> {
                        // handle action2
                        val intent = Intent(this, CardSetupManualActivity::class.java)
                        intent.putExtra("action", R.id.cardsetup_create)
                        intent.putExtra("card_id", -1)
                        startActivity(intent)
                        true
                    }
                    R.id.action4 -> {
                        if (card != null) {
                            // handle action2
                            val intent = Intent(this, CardSetupManualActivity::class.java)
                            intent.putExtra("action", R.id.cardsetup_edit)
                            intent.putExtra("card_id", card.id)
                            startActivity(intent)
                        }
                        true
                    }
                    R.id.action5 -> {
                        if (card != null) {
                            // handle action2
                            intent.putExtra("action", R.id.cardsetup_delete)
                            intent.putExtra("card_id", card.id)
                            val builder = AlertDialog.Builder(this)
                            builder.setTitle("Delete card")
                            builder.setMessage("Are you sure you want to delete this card?")

                            // Set the alert dialog positive (yes) button
                            builder.setPositiveButton("Yes") { _, _ ->
                                // Do the task you want after user confirmed action here
                                GlobalScope.launch(Dispatchers.IO) { // launch a new coroutine in background and continue
                                    val cards = appDatabase.cardDao().delete(card)
                                    withContext(Dispatchers.Main) {
                                        val intent =
                                            Intent(this@MainActivity, MainActivity::class.java)
                                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                                        startActivity(intent)
                                    }
                                }
                            }

                            // Set the alert dialog negative (no) button
                            builder.setNegativeButton("No") { _, _ ->

                            }

// Create the AlertDialog
                            val dialog: AlertDialog = builder.create()
// Finally, display the alert dialog
                            dialog.show()
                        }
                        true
                    }
                    else -> false
                }
            }
            popupMenu.show()
        }



        /*button.setOnClickListener {
            val intent = Intent(this, VirtualBoltcardActivity::class.java)
            startActivity(intent)
        }*/
        initNFCFunction()
        initService()

    }

    override fun onResume() {
        super.onResume()
        GlobalScope.launch(Dispatchers.IO) { // launch a new coroutine in background and continue
            val cardcount = cardDao.getAll().count()
            withContext(Dispatchers.Main) {
                if (cardcount == 0) {
                    val intent = Intent(this@MainActivity, NoCardYetActivity::class.java)
                    startActivity(intent)
                }
            }
        }
        if (mNfcAdapter?.isEnabled == true) {
            //TODO: Handling if NFC has been disabled
        }
        val active_card_id = loadData(this, "card_id")
        var active_card_index = 0
        GlobalScope.launch(Dispatchers.IO) { // launch a new coroutine in background and continue
            val cards = appDatabase.cardDao().getAll()
            var index = 0
            cards.forEach { card ->
                Log.i("Database content", card.toString())
                if (!active_card_id.isEmpty() && card.id == active_card_id.toInt()){
                    active_card_index = index
                }
                index++
                //appDatabase.cardDao().delete(card)
            }
            withContext(Dispatchers.Main) {
                // Set the adapter in the main thread
                viewPager.adapter = CardAdapter(cards)
                viewPager.setCurrentItem(active_card_index, false)
            }
        }
        val pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                // 'position' is the index of the current item
                // You can use this index to fetch data from your adapter
                val adapter =  viewPager.adapter as? CardAdapter
                currentCard = adapter?.getCard(position)!!
                val card = currentCard!!
                GlobalScope.launch(Dispatchers.IO) {
                    card.activate(this@MainActivity)
                    withContext(Dispatchers.Main) {
                        //add more types here
                        Log.i("CardType", card.type)
                        if (card.type == "AdditionalDataLNbits") {
                            val additionalDataFragment =
                                AdditionalDataLNbitsFragment.newInstance(card.id)
                            supportFragmentManager.beginTransaction()
                                //.setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
                                .replace(R.id.additionaldata_container, additionalDataFragment)
                                .commit()
                        }
                        else{
                            val fragmentToRemove = supportFragmentManager.findFragmentById(R.id.additionaldata_container)
                            if (fragmentToRemove != null) {
                                supportFragmentManager.beginTransaction()
                                    .remove(fragmentToRemove)
                                    .commit()
                            }
                        }
                    }
                }
                // Perform some action with the current item
            }
        }
        viewPager.registerOnPageChangeCallback(pageChangeCallback)

    }
    override fun onDestroy() {
        super.onDestroy()
        if (::pageChangeCallback.isInitialized) {
            viewPager.unregisterOnPageChangeCallback(pageChangeCallback)
        }
    }
    private fun initNFCFunction() {
        if (supportNfcHceFeature()) {
            // Prevent phone that doesn't support NFC to trigger dialog
            if (supportNfcHceFeature()) {
                showTurnOnNfcDialog()
            }
        }
    }

    private fun initService() {

        //val keyEditor = sharedPreferences.edit()
        val countertxt = loadData(this, "counter")
        val lnurltemplate = loadData(this, "lnurltemplate")
        val key1 = sharedPreferencesHelper.loadData(this,"key1")
        val key2 = sharedPreferencesHelper.loadData(this,"key2")
        val uid = sharedPreferencesHelper.loadData(this,"uid")
        if ((key1.length == 32)
            || (key2.length == 32)
            || (uid.length == 7)
            || (uid.length == 14)
            || (lnurltemplate.isNotEmpty())
            || (countertxt.isNotEmpty())
        ) {
            //showUid.setText(uid)
            val intent = Intent(this@MainActivity, KHostApduService::class.java)
            startService(intent)
        }
    }

    private fun supportNfcHceFeature() =
        checkNFCEnable() && packageManager.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)

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

    private fun checkNFCEnable(): Boolean {
        return if (mNfcAdapter == null) {
            //textView.text = getString(R.string.tv_noNfc)
            false
        } else {
            mNfcAdapter?.isEnabled == true
        }
    }

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

class CardAdapter(private val cards: List<Card>) : RecyclerView.Adapter<CardAdapter.CardViewHolder>() {

    class CardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardImage: ImageView = itemView.findViewById(R.id.card_image)
        val cardUid: TextView = itemView.findViewById(R.id.card_uid)
        val cardName: TextView = itemView.findViewById(R.id.card_name)
        val cardId: TextView = itemView.findViewById(R.id.card_id)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.card_view, parent, false)
        return CardViewHolder(view)
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        val card = cards[position]
        val context = holder.itemView.context
        val drawableId = context.resources.getIdentifier(card.drawableName, "drawable", context.packageName)
        val drawable = ContextCompat.getDrawable(holder.itemView.context, drawableId)
        //val drawable = R.drawable.virtualboltcard
        holder.cardImage.setImageDrawable(drawable)
        //holder.cardUid.text = formatUid(card.uid)
        //holder.cardName.text = card.name
        GlobalScope.launch(Dispatchers.IO) { // launch a new coroutine in background and continue
            val context = holder.itemView.context
            var bitmap=drawTextToDrawable(
                context,
                drawableId,
                formatUid(card.uid),
                50,
                380,
                40
            )
            val bitmap2=drawTextToBitmap(
                context,
                bitmap,
                formatUid(card.name),
                50,
                120,
                70
            )
            withContext(Dispatchers.Main) {
                holder.cardImage.setImageBitmap(bitmap)
            }
        }
    }
    override fun getItemCount() = cards.size

    fun getCard(position: Int): Card {
        return cards[position]
    }
    private fun formatUid(uid: String): String {
        // Format the UID as needed
        return uid
    }

    private fun drawTextToDrawable(
        context: Context,
        drawableId: Int,
        text: String,
        x: Int,
        y: Int,
        size: Int,
    ):Bitmap{
        val resources = context.resources
        val scale = resources.displayMetrics.density
        Log.i("BitmapScale", scale.toString())
        var bitmap = BitmapFactory.decodeResource(resources, drawableId)
        var bitmapConfig: android.graphics.Bitmap.Config? = bitmap.config
        // set default bitmap config if none
        if (bitmapConfig == null) {
            bitmapConfig = android.graphics.Bitmap.Config.ARGB_8888
        }
        // resource bitmaps are immutable,
        // so we need to convert it to mutable one
        bitmap = bitmap.copy(bitmapConfig, true)
        return drawTextToBitmap(
            context,
            bitmap,
            text,
            x,
            y,
            size
        )
    }
    private fun drawTextToBitmap(
        context: Context,
        bitmap: Bitmap,
        text: String,
        x: Int,
        y: Int,
        size: Int,
    ): Bitmap {
        val resources = context.resources
        val scale = resources.displayMetrics.density
        val canvas = Canvas(bitmap)
        // new antialised Paint
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        // text color - #3D3D3D
        paint.color = Color.rgb(255, 255, 255)
        // text size in pixels
        paint.textSize = (size * scale).toInt().toFloat()
        // text shadow
        paint.setShadowLayer(1f, 0f, 1f, Color.DKGRAY)

        // draw text to the Canvas center
        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)
        //val x = (bitmap.width - bounds.width()) / 2
        //val y = (bitmap.height + bounds.height()) / 2

        canvas.drawText(text, scale * x.toFloat(), scale * y.toFloat(), paint)

        return bitmap
    }
}

